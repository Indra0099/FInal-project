package com.shardquery.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardquery.ai.GeminiGateway;
import com.shardquery.auth.AccountPrincipal;
import com.shardquery.document.*;
import com.shardquery.infrastructure.*;
import com.shardquery.persistence.DocumentRecord;
import com.shardquery.query.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final QueryService queries;
  private final DocumentService documents;
  private final GeminiGateway ai;
  private final ShardManager shards;
  private final CacheService cache;
  private final VectorStore vectors;
  private final ObjectMapper mapper;

  public ApiController(
      QueryService queries,
      DocumentService documents,
      GeminiGateway ai,
      ShardManager shards,
      CacheService cache,
      VectorStore vectors,
      ObjectMapper mapper) {
    this.queries = queries;
    this.documents = documents;
    this.ai = ai;
    this.shards = shards;
    this.cache = cache;
    this.vectors = vectors;
    this.mapper = mapper;
  }

  @GetMapping("/health")
  public Map<String, Object> health() throws Exception {
    String cached = cache.get("health");
    if (cached != null)
      return mapper.readValue(cached, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    var h = new LinkedHashMap<String, Object>();
    h.put("application", "UP");
    h.put("shards", shards.health());
    h.put("redis", cache.healthy() ? "UP" : "DOWN");
    h.put("qdrant", vectors.healthy() ? "UP" : "DOWN");
    h.put("aiConfigured", ai.configured());
    h.put("aiProvider", "Google Gemini");
    h.put("paidProviderFallback", false);
    h.put("model", ai.model());
    h.put("embeddingModel", ai.embeddingModel());
    h.put("workspace", "Personal local workspace");
    cache.put("health", mapper.writeValueAsString(h), 5);
    return h;
  }

  @PostMapping("/queries")
  public ResponseEntity<?> query(
      @Valid @RequestBody QueryTypes.Request request,
      @AuthenticationPrincipal AccountPrincipal user) {
    var job = queries.submit(request, user.id());
    return ResponseEntity.accepted().body(Map.of("id", job.id, "status", job.status));
  }

  @GetMapping("/queries/{id}")
  public Map<String, Object> query(
      @PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal user) {
    return queries.view(queries.get(id, user.id()));
  }

  @GetMapping("/queries")
  public List<Map<String, Object>> history(@AuthenticationPrincipal AccountPrincipal user) {
    return queries.history(user.id());
  }

  @GetMapping("/documents")
  public List<DocumentRecord> documents(@AuthenticationPrincipal AccountPrincipal user) {
    return documents.list(user.id());
  }

  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentRecord> upload(
      @RequestParam MultipartFile file,
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) Long customerId,
      @AuthenticationPrincipal AccountPrincipal user)
      throws Exception {
    return ResponseEntity.accepted().body(documents.upload(file, productId, customerId, user.id()));
  }

  @PostMapping("/documents/{id}/retry")
  public DocumentRecord retry(
      @PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal user) {
    return documents.retry(id, user.id());
  }

  @GetMapping("/documents/{id}/download")
  public ResponseEntity<byte[]> download(
      @PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal user) throws Exception {
    var doc = documents.get(id, user.id());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(doc.name, java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(documents.bytes(id, user.id()));
  }
}
