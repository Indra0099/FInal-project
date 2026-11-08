package com.shardquery.document;

import com.shardquery.ai.GeminiGateway;
import com.shardquery.infrastructure.CacheService;
import com.shardquery.persistence.*;
import com.shardquery.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
  private final DocumentRepository docs;
  private final DocumentExtractor extractor;
  private final GeminiGateway ai;
  private final VectorStore vectors;
  private final KafkaTemplate<String, String> kafka;
  private final Path root;
  private final CacheService cache;

  public DocumentService(
      DocumentRepository docs,
      DocumentExtractor extractor,
      GeminiGateway ai,
      VectorStore vectors,
      KafkaTemplate<String, String> kafka,
      CacheService cache,
      @Value("${app.upload-dir}") String dir)
      throws Exception {
    this.docs = docs;
    this.extractor = extractor;
    this.ai = ai;
    this.vectors = vectors;
    this.kafka = kafka;
    this.cache = cache;
    root = Path.of(dir).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  public synchronized DocumentRecord upload(
      MultipartFile file, Long product, Long customer, UUID owner) throws Exception {
    Objects.requireNonNull(owner);
    if (product != null && product < 1
        || customer != null && (customer < 1 || customer > Integer.MAX_VALUE))
      throw new IllegalArgumentException("Use positive product/customer IDs.");
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new IllegalArgumentException("Choose a non-empty PDF, DOCX or TXT file up to 10 MB.");
    String name =
        Optional.ofNullable(file.getOriginalFilename())
            .orElse("document.txt")
            .replaceAll("[\\\\/\\p{Cntrl}]", "_");
    if (name.length() > 180)
      throw new IllegalArgumentException("Filename must be under 180 characters.");
    byte[] bytes = file.getBytes();
    extractor.chunks(extractor.extract(bytes, name));
    var digest = MessageDigest.getInstance("SHA-256");
    digest.update(bytes);
    digest.update(
        ("|" + owner + "|" + product + "|" + customer + "|" + ai.embeddingModel())
            .getBytes(StandardCharsets.UTF_8));
    String fingerprint = HexFormat.of().formatHex(digest.digest());
    var existing = docs.findByFingerprint(fingerprint);
    if (existing.isPresent()) return existing.get();
    if (docs.countByUserId(owner) >= 100)
      throw new ApiException(409, "This demonstration workspace is limited to 100 documents.");
    var doc = new DocumentRecord();
    doc.id = UUID.randomUUID();
    doc.userId = owner;
    doc.name = name;
    doc.fingerprint = fingerprint;
    doc.productId = product;
    doc.customerId = customer;
    doc.createdAt = Instant.now();
    doc.updatedAt = doc.createdAt;
    doc.nextDispatch = doc.createdAt;
    doc.status = ai.configured() ? "QUEUED" : "WAITING_FOR_KEY";
    doc.embeddingModel = ai.embeddingModel();
    Path path = path(doc.id);
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    try {
      return docs.save(doc);
    } catch (Exception e) {
      Files.deleteIfExists(path);
      throw e;
    }
  }

  public List<DocumentRecord> list(UUID owner) {
    return docs.findTop100ByUserIdOrderByCreatedAtDesc(owner);
  }

  public DocumentRecord get(UUID id, UUID owner) {
    return docs.findByIdAndUserId(id, owner)
        .orElseThrow(() -> new ApiException(404, "Document not found."));
  }

  public synchronized DocumentRecord retry(UUID id, UUID owner) {
    ai.requireConfigured();
    var d = get(id, owner);
    if (!Set.of("FAILED", "WAITING_FOR_KEY").contains(d.status))
      throw new ApiException(409, "Only failed or waiting documents can be retried.");
    d.status = "QUEUED";
    d.attempts = 0;
    d.error = null;
    d.nextDispatch = Instant.now();
    d.updatedAt = Instant.now();
    return docs.save(d);
  }

  public byte[] bytes(UUID id, UUID owner) throws Exception {
    get(id, owner);
    return Files.readAllBytes(path(id));
  }

  private Path path(UUID id) {
    return root.resolve(id + ".bin");
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    for (var doc : docs.findByStatus("PROCESSING")) {
      doc.status = "QUEUED";
      doc.error = "Indexing interrupted by restart; retry scheduled.";
      doc.nextDispatch = Instant.now();
      docs.save(doc);
    }
  }

  @Scheduled(fixedDelay = 5000)
  public void dispatch() {
    if (!ai.configured()) return;
    for (var doc :
        docs.findTop10ByStatusAndNextDispatchBeforeOrderByCreatedAtAsc("QUEUED", Instant.now())) {
      doc.nextDispatch = Instant.now().plusSeconds(30);
      docs.save(doc);
      try {
        kafka
            .send("shardquery.documents", doc.id.toString(), doc.id.toString())
            .get(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        /* Durable pending row remains eligible for the next dispatch. */
      }
    }
  }

  @KafkaListener(topics = "shardquery.documents", concurrency = "1")
  public void consume(String id) {
    UUID uuid;
    try {
      uuid = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return;
    }
    var found = docs.findById(uuid);
    if (found.isEmpty()) return;
    var doc = found.get();
    if (!"QUEUED".equals(doc.status)) return;
    doc.status = "PROCESSING";
    doc.attempts++;
    doc.updatedAt = Instant.now();
    docs.save(doc);
    cache.put("document:" + id, "PROCESSING", 120);
    try {
      List<String> chunks =
          extractor.chunks(extractor.extract(Files.readAllBytes(path(doc.id)), doc.name));
      var embeddings = ai.embed(chunks);
      doc.embeddingModel = ai.embeddingModel();
      vectors.upsert(doc, chunks, embeddings);
      doc.status = "READY";
      doc.chunks = chunks.size();
      doc.error = null;
    } catch (Exception e) {
      doc.status = doc.attempts >= 3 ? "FAILED" : "QUEUED";
      doc.error =
          e instanceof ApiException || e instanceof IllegalArgumentException
              ? e.getMessage()
              : "Document extraction or indexing failed. Verify the file and retry.";
      doc.nextDispatch = Instant.now().plusSeconds(10L * doc.attempts);
    }
    doc.updatedAt = Instant.now();
    docs.save(doc);
    cache.put("document:" + id, doc.status, 120);
  }
}
