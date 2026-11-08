package com.shardquery.query;

import static com.shardquery.query.QueryTypes.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardquery.ai.GeminiGateway;
import com.shardquery.document.VectorStore;
import com.shardquery.infrastructure.CacheService;
import com.shardquery.persistence.*;
import com.shardquery.web.ApiException;
import com.shardquery.workspace.WorkspaceService;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class QueryService {
  private final QueryJobRepository jobs;
  private final WorkspaceService personal;
  private final DocumentRepository docs;
  private final SqlEngine sql;
  private final GeminiGateway ai;
  private final VectorStore vectors;
  private final CacheService cache;
  private final ObjectMapper mapper;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          2,
          4,
          60,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(12),
          new ThreadPoolExecutor.AbortPolicy());

  public QueryService(
      QueryJobRepository jobs,
      WorkspaceService personal,
      DocumentRepository docs,
      SqlEngine sql,
      GeminiGateway ai,
      VectorStore vectors,
      CacheService cache,
      ObjectMapper mapper) {
    this.jobs = jobs;
    this.personal = personal;
    this.docs = docs;
    this.sql = sql;
    this.ai = ai;
    this.vectors = vectors;
    this.cache = cache;
    this.mapper = mapper;
  }

  public QueryJob submit(Request request, UUID owner) {
    Objects.requireNonNull(owner);
    if (request.source() == DataSource.PERSONAL
        && (request.mode() == Mode.RAG || request.mode() == Mode.HYBRID))
      throw new ApiException(400, "Choose Auto or Database mode to query personal tables.");
    if (request.tableId() != null
        && (request.mode() != Mode.SQL && request.mode() != Mode.AUTO
            || request.plan() != null
            || request.source() == DataSource.DEMO))
      throw new ApiException(
          400,
          "Select Database mode for your own tables. Guided sample plans cannot query personal"
              + " tables.");
    if (request.plan() != null) {
      if (request.source() == DataSource.PERSONAL)
        throw new ApiException(400, "Guided sample plans cannot query personal databases.");
      request.plan().validated();
      if (request.mode() != Mode.SQL || request.plan().mode() != Mode.SQL)
        throw new IllegalArgumentException(
            "Explicit plans are only accepted in the guided SQL explorer.");
    } else ai.requireConfigured();
    var job = new QueryJob();
    job.id = UUID.randomUUID();
    job.userId = owner;
    job.question = request.question();
    job.mode = request.mode().name();
    job.status = "QUEUED";
    job.createdAt = Instant.now();
    jobs.save(job);
    try {
      executor.execute(() -> run(job.id, request));
    } catch (RejectedExecutionException e) {
      job.status = "FAILED";
      job.error = "The query queue is full. Please retry shortly.";
      job.completedAt = Instant.now();
      jobs.save(job);
      throw new ApiException(429, job.error);
    }
    return job;
  }

  public Map<String, Object> view(QueryJob j) {
    var map = new LinkedHashMap<String, Object>();
    map.put("id", j.id);
    map.put("question", j.question);
    map.put("mode", j.mode);
    map.put("status", j.status);
    map.put("createdAt", j.createdAt);
    map.put("completedAt", j.completedAt);
    map.put("error", j.error);
    try {
      map.put("result", j.resultJson == null ? null : mapper.readTree(j.resultJson));
    } catch (Exception e) {
      map.put("result", null);
    }
    return map;
  }

  public QueryJob get(UUID id, UUID owner) {
    return jobs.findByIdAndUserId(id, owner)
        .orElseThrow(() -> new ApiException(404, "Query not found."));
  }

  public List<Map<String, Object>> history(UUID owner) {
    return jobs.findTop50ByUserIdOrderByCreatedAtDesc(owner).stream().map(this::view).toList();
  }

  private void run(UUID id, Request request) {
    var job = jobs.findById(id).orElseThrow();
    long start = System.nanoTime();
    try {
      job.status = "RUNNING";
      jobs.save(job);
      cache.put("job:" + id, "RUNNING", 180);
      var ready =
          docs.findByUserIdAndStatus(job.userId, "READY").stream()
              .filter(d -> ai.embeddingModel().equals(d.embeddingModel))
              .toList();
      var revisions = ready.stream().map(d -> d.id + ":" + d.updatedAt).sorted().toList();
      String key =
          "answer:"
              + HexFormat.of()
                  .formatHex(
                      MessageDigest.getInstance("SHA-256")
                          .digest(
                              (job.userId
                                      + mapper.writeValueAsString(request)
                                      + revisions
                                      + ai.model())
                                  .getBytes(StandardCharsets.UTF_8)));
      boolean cacheable =
          request.tableId() == null
              && (request.source() == DataSource.DEMO || request.plan() != null);
      String existing = cacheable ? cache.get(key) : null;
      var usedTables = new ArrayList<com.shardquery.workspace.PersonalQueryEngine.Table>();
      String actualSource = "DEMO";

      Result result;
      if (existing != null) {
        var previous = mapper.readValue(existing, Result.class);
        result =
            new Result(
                previous.mode(),
                previous.answer(),
                previous.rows(),
                previous.sources(),
                previous.traces(),
                previous.warnings(),
                previous.plan(),
                true,
                previous.generatedAt(),
                elapsed(start),
                previous.answerMethod());
      } else {
        Plan plan;
        DatabaseResult db;
        com.shardquery.workspace.PersonalQueryEngine.Plan routed = null;
        if (request.tableId() == null
            && request.plan() == null
            && request.mode() != Mode.RAG
            && request.source() != DataSource.DEMO)
          routed = personal.route(job.userId, request.question(), request.mode(), request.source());
        if (request.tableId() != null) {
          usedTables.addAll(
              personal.catalog(job.userId).stream()
                  .filter(t -> t.id().equals(request.tableId()))
                  .toList());
          actualSource = "PERSONAL";
          var data = personal.query(job.userId, request.tableId(), request.question());
          plan =
              new Plan(
                  Mode.SQL,
                  Intent.CUSTOM_TABLE,
                  null,
                  null,
                  null,
                  null,
                  data.limit(),
                  data.explanation());
          db = data.data();
        } else if (routed != null && routed.target().equals("PERSONAL")) {
          var data = personal.executeAcross(job.userId, routed);
          var selected = routed.tables();
          usedTables.addAll(
              personal.catalog(job.userId).stream()
                  .filter(t -> selected.contains(t.id()))
                  .toList());
          actualSource = "PERSONAL";
          plan =
              new Plan(
                  Mode.SQL,
                  Intent.CUSTOM_TABLE,
                  null,
                  null,
                  null,
                  null,
                  data.limit(),
                  data.explanation());
          db = data.data();
        } else {
          plan =
              request.plan() != null
                  ? request.plan()
                  : request.mode() == Mode.RAG
                          || routed != null && routed.target().equals("DOCUMENTS")
                      ? new Plan(
                          Mode.RAG,
                          Intent.PRODUCTS,
                          null,
                          null,
                          null,
                          null,
                          50,
                          "Search uploaded documents.")
                      : ai.plan(request.question(), request.mode());
          db =
              plan.mode() == Mode.RAG
                  ? new DatabaseResult(List.of(), List.of(), List.of(), List.of())
                  : sql.execute(plan);
        }
        if (plan.mode() == Mode.RAG) actualSource = "DOCUMENTS";
        var warnings = new ArrayList<>(db.warnings());
        List<Source> sources = List.of();
        if (plan.mode() != Mode.SQL) {
          if (plan.mode() == Mode.HYBRID && db.scope().isEmpty())
            warnings.add(
                "No matched product records were available to associate with documents. No"
                    + " unrelated documents were used.");
          else if (ready.isEmpty())
            warnings.add(
                "No indexed documents are ready for the configured embedding model. Upload"
                    + " documents and wait for Ready.");
          else {
            try {
              sources =
                  vectors.search(
                      ai.embed(List.of(request.question())).getFirst(),
                      db.scope(),
                      plan.mode() == Mode.HYBRID,
                      ai.embeddingModel(),
                      ready.stream().map(d -> d.id.toString()).toList());
              if (sources.isEmpty())
                warnings.add(
                    "No document excerpts matched this query and its record associations.");
            } catch (ApiException e) {
              warnings.add(e.getMessage());
            }
          }
        }
        String answer;
        String method;
        if (request.plan() != null) {
          answer =
              db.rows().isEmpty()
                  ? (warnings.isEmpty()
                      ? "No records match these filters."
                      : "No complete result is available. Review the warnings below.")
                  : "Retrieved "
                      + db.rows().size()
                      + " matching record"
                      + (db.rows().size() == 1 ? "" : "s")
                      + " from the selected databases. Review the table and execution details"
                      + " below.";
          method = "DETERMINISTIC_SQL";
        } else if (db.rows().isEmpty() && sources.isEmpty()) {
          answer =
              warnings.isEmpty()
                  ? "No matching database records were found."
                  : "There is not enough retrieved evidence to answer this question. Review the"
                      + " warnings below.";
          method = "NO_EVIDENCE";
        } else {
          try {
            answer = ai.answer(request.question(), db.rows(), sources, warnings);
            method = "GEMINI";
          } catch (ApiException e) {
            warnings.add(e.getMessage());
            answer =
                "Evidence was retrieved, but the AI summary is unavailable. Review the actual"
                    + " records and document excerpts below.";
            method = "EVIDENCE_ONLY";
          }
        }
        result =
            new Result(
                plan.mode(),
                answer,
                db.rows(),
                sources,
                db.traces(),
                warnings,
                plan,
                false,
                Instant.now().toString(),
                elapsed(start),
                method);
        if (warnings.isEmpty() && cacheable) cache.put(key, mapper.writeValueAsString(result), 45);
      }
      var saved = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(result);
      saved.put("dataSource", result.mode() == Mode.RAG ? "DOCUMENTS" : actualSource);
      saved.put("requestSource", request.source().name());
      if (request.tableId() != null) saved.put("requestTableId", request.tableId().toString());
      saved.set("tables", mapper.valueToTree(usedTables));
      if (usedTables.size() == 1) saved.put("tableId", usedTables.getFirst().id().toString());
      job.resultJson = mapper.writeValueAsString(saved);
      job.status = result.warnings().isEmpty() ? "COMPLETE" : "PARTIAL";
    } catch (Exception e) {
      job.status = "FAILED";
      job.error =
          e instanceof ApiException || e instanceof IllegalArgumentException
              ? e.getMessage()
              : "The query could not finish. Check service status and try again.";
    }
    job.completedAt = Instant.now();
    jobs.save(job);
    cache.put("job:" + id, job.status, 180);
  }

  private static long elapsed(long start) {
    return (System.nanoTime() - start) / 1000000;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    for (var job : jobs.findByStatusIn(List.of("RUNNING", "QUEUED"))) {
      job.status = "FAILED";
      job.error = "Interrupted by application restart. Run this query again.";
      job.completedAt = Instant.now();
      jobs.save(job);
    }
  }

  @PreDestroy
  public void stop() {
    executor.shutdownNow();
  }
}
