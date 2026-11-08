package com.shardquery.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardquery.persistence.DocumentRecord;
import com.shardquery.query.QueryTypes.*;
import com.shardquery.web.ApiException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
public class VectorStore {
  private final RestClient client;
  private final String collection;

  public VectorStore(
      @Value("${app.qdrant-url}") String url, @Value("${app.collection}") String collection) {
    this.collection = collection;
    var f =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    f.setReadTimeout(Duration.ofSeconds(12));
    client = RestClient.builder().baseUrl(url).requestFactory(f).build();
  }

  public boolean healthy() {
    try {
      client.get().uri("/readyz").retrieve().toBodilessEntity();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private synchronized void ensureCollection() {
    try {
      client.get().uri("/collections/" + collection).retrieve().toBodilessEntity();
    } catch (HttpClientErrorException.NotFound e) {
      client
          .put()
          .uri("/collections/" + collection)
          .body(Map.of("vectors", Map.of("size", 1536, "distance", "Cosine")))
          .retrieve()
          .toBodilessEntity();
    }
  }

  public void upsert(DocumentRecord doc, List<String> chunks, List<List<Double>> embeddings) {
    try {
      ensureCollection();
      var points = new ArrayList<Map<String, Object>>();
      for (int i = 0; i < chunks.size(); i++) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("document_id", doc.id.toString());
        payload.put("filename", doc.name);
        payload.put("chunk", i + 1);
        payload.put("text", chunks.get(i));
        payload.put("embedding_model", doc.embeddingModel);
        if (doc.productId != null) payload.put("product_id", doc.productId);
        if (doc.customerId != null) payload.put("customer_id", doc.customerId);
        String id =
            UUID.nameUUIDFromBytes((doc.id + ":" + i).getBytes(StandardCharsets.UTF_8)).toString();
        points.add(Map.of("id", id, "vector", embeddings.get(i), "payload", payload));
      }
      client
          .put()
          .uri("/collections/" + collection + "/points?wait=true")
          .body(Map.of("points", points))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new ApiException(503, "Document storage is unavailable. Please retry indexing later.");
    }
  }

  public List<Source> search(
      List<Double> vector, List<Scope> scope, boolean hybrid, String model, List<String> readyIds) {
    if (readyIds.isEmpty() || hybrid && scope.isEmpty()) return List.of();
    var must = new ArrayList<Object>();
    must.add(match("embedding_model", model));
    must.add(Map.of("key", "document_id", "match", Map.of("any", readyIds)));
    if (hybrid) {
      var alternatives = new ArrayList<Object>();
      for (var pair : scope) {
        var pairFilters = new ArrayList<Object>();
        pairFilters.add(match("product_id", pair.productId()));
        if (pair.customerId() > 0) pairFilters.add(match("customer_id", pair.customerId()));
        alternatives.add(Map.of("must", pairFilters));
      }
      must.add(Map.of("should", alternatives));
    }
    try {
      var response =
          client
              .post()
              .uri("/collections/" + collection + "/points/query")
              .body(
                  Map.of(
                      "query",
                      vector,
                      "limit",
                      6,
                      "with_payload",
                      true,
                      "score_threshold",
                      0.18,
                      "filter",
                      Map.of("must", must)))
              .retrieve()
              .body(JsonNode.class);
      var result = new ArrayList<Source>();
      for (var point : response.path("result").path("points")) {
        var p = point.path("payload");
        result.add(
            new Source(
                "D" + (result.size() + 1),
                UUID.fromString(p.path("document_id").asText()),
                p.path("filename").asText(),
                p.path("chunk").asInt(),
                p.path("text").asText(),
                point.path("score").asDouble(),
                p.has("product_id") ? p.get("product_id").asLong() : null,
                p.has("customer_id") ? p.get("customer_id").asLong() : null));
      }
      return result;
    } catch (HttpClientErrorException.NotFound e) {
      return List.of();
    } catch (RestClientException e) {
      throw new ApiException(503, "Document search is temporarily unavailable.");
    }
  }

  private static Map<String, Object> match(String key, Object value) {
    return Map.of("key", key, "match", Map.of("value", value));
  }
}
