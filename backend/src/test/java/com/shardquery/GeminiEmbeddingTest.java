package com.shardquery;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardquery.ai.GeminiGateway;
import com.shardquery.web.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;

class GeminiEmbeddingTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void acceptsRealGeminiResponseWithoutIndexesInInputOrder() {
    var first = new ArrayList<>(Collections.nCopies(3072, 0.0));
    var second = new ArrayList<>(Collections.nCopies(3072, 0.0));
    first.set(0, 1.0);
    second.set(1, 1.0);
    var response =
        mapper.valueToTree(
            Map.of("data", List.of(Map.of("embedding", first), Map.of("embedding", second))));
    var parsed = GeminiGateway.parseEmbeddings(response, 2);
    assertEquals(1.0, parsed.get(0).get(0));
    assertEquals(1.0, parsed.get(1).get(1));
  }

  @Test
  void rejectsPartialMixedAndDuplicateIndexedResponses() {
    var vector = Collections.nCopies(1536, 1.0);
    var single = mapper.valueToTree(Map.of("data", List.of(Map.of("embedding", vector))));
    assertThrows(ApiException.class, () -> GeminiGateway.parseEmbeddings(single, 2));
    var mixed =
        mapper.valueToTree(
            Map.of(
                "data",
                List.of(Map.of("index", 0, "embedding", vector), Map.of("embedding", vector))));
    assertThrows(ApiException.class, () -> GeminiGateway.parseEmbeddings(mixed, 2));
    var duplicate =
        mapper.valueToTree(
            Map.of(
                "data",
                List.of(
                    Map.of("index", 0, "embedding", vector),
                    Map.of("index", 0, "embedding", vector))));
    assertThrows(ApiException.class, () -> GeminiGateway.parseEmbeddings(duplicate, 2));
  }

  @Test
  void usesExplicitIndexesWhenTheProviderIncludesThem() {
    var first = new ArrayList<>(Collections.nCopies(1536, 0.0));
    var second = new ArrayList<>(Collections.nCopies(1536, 0.0));
    first.set(0, 1.0);
    second.set(1, 1.0);
    var response =
        mapper.valueToTree(
            Map.of(
                "data",
                List.of(
                    Map.of("index", 1, "embedding", second),
                    Map.of("index", 0, "embedding", first))));
    assertEquals(1.0, GeminiGateway.parseEmbeddings(response, 2).get(0).get(0));
  }

  @Test
  void reducesNativeEmbeddingAndNormalizesTheRetainedPrefix() {
    var vector = new ArrayList<>(Collections.nCopies(3072, 0.0));
    vector.set(0, 3.0);
    vector.set(1, 4.0);
    vector.set(2000, 999.0);
    var normalized = GeminiGateway.normalizeEmbedding(vector);
    assertEquals(1536, normalized.size());
    assertEquals(0.6, normalized.get(0), 1e-12);
    assertEquals(0.8, normalized.get(1), 1e-12);
    assertEquals(1.0, normalized.stream().mapToDouble(v -> v * v).sum(), 1e-12);
    assertEquals(3072, vector.size());
  }

  @Test
  void rejectsInvalidDimensionsZeroAndNonFiniteEmbeddings() {
    assertThrows(ApiException.class, () -> GeminiGateway.normalizeEmbedding(List.of(1.0)));
    assertThrows(
        ApiException.class, () -> GeminiGateway.normalizeEmbedding(Collections.nCopies(1536, 0.0)));
    var vector = new ArrayList<>(Collections.nCopies(1536, 1.0));
    vector.set(20, Double.NaN);
    assertThrows(ApiException.class, () -> GeminiGateway.normalizeEmbedding(vector));
  }

  @Test
  void acceptsAndNormalizesAnAlreadyReducedVector() {
    var normalized = GeminiGateway.normalizeEmbedding(Collections.nCopies(1536, 2.0));
    assertEquals(1536, normalized.size());
    assertEquals(1.0, normalized.stream().mapToDouble(v -> v * v).sum(), 1e-12);
  }
}
