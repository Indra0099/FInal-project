package com.shardquery.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public final class QueryTypes {
  private QueryTypes() {}

  public enum Mode {
    AUTO,
    SQL,
    RAG,
    HYBRID
  }

  public enum DataSource {
    AUTO,
    PERSONAL,
    DEMO
  }

  public enum Intent {
    PURCHASES,
    CUSTOMERS,
    PRODUCTS,
    REVIEWS,
    INVENTORY,
    UNSUPPORTED,
    CUSTOM_TABLE
  }

  public record Plan(
      Mode mode,
      Intent intent,
      String category,
      BigDecimal minPrice,
      Integer customerId,
      Long productId,
      int limit,
      String explanation) {
    public Plan validated() {
      if (mode == null
          || mode == Mode.AUTO
          || intent == null
          || intent == Intent.UNSUPPORTED
          || intent == Intent.CUSTOM_TABLE)
        throw new IllegalArgumentException(
            "Sample commerce mode supports customer, purchase, product, inventory, review and"
                + " document questions. Choose Automatically choose or My databases for your own"
                + " data.");
      if (limit < 1 || limit > 100)
        throw new IllegalArgumentException("Result limit must be 1–100.");
      if (minPrice != null
          && (minPrice.signum() < 0 || minPrice.compareTo(new BigDecimal("1000000000")) > 0))
        throw new IllegalArgumentException("Price is outside the supported range.");
      if (customerId != null && customerId < 1 || productId != null && productId < 1)
        throw new IllegalArgumentException("Identifiers must be positive.");
      if (category != null && category.length() > 80)
        throw new IllegalArgumentException("Category is too long.");
      if (intent == Intent.CUSTOMERS && (category != null || minPrice != null || productId != null))
        throw new IllegalArgumentException("Customer profiles support only customer-ID filtering.");
      if ((intent == Intent.PRODUCTS || intent == Intent.INVENTORY) && customerId != null)
        throw new IllegalArgumentException("Use purchases to filter products by customer.");
      if (intent == Intent.REVIEWS && minPrice != null)
        throw new IllegalArgumentException(
            "Review queries do not support a price filter; use a hybrid purchase query.");
      return this;
    }
  }

  public record Request(
      @NotBlank @Size(min = 3, max = 2000) String question,
      @NotNull Mode mode,
      @Valid Plan plan,
      UUID tableId,
      DataSource source) {
    public Request {
      source = source == null ? DataSource.AUTO : source;
    }

    public Request(String question, Mode mode, Plan plan, UUID tableId) {
      this(question, mode, plan, tableId, DataSource.AUTO);
    }

    public Request(String question, Mode mode, Plan plan) {
      this(question, mode, plan, null, DataSource.AUTO);
    }
  }

  public record Trace(
      String shard,
      Integer bucket,
      String sql,
      Map<String, Object> parameters,
      int rows,
      long durationMs,
      String status) {}

  public record Source(
      String id,
      UUID documentId,
      String filename,
      int chunk,
      String text,
      double score,
      Long productId,
      Long customerId) {}

  public record Scope(long productId, long customerId) {}

  public record DatabaseResult(
      List<Map<String, Object>> rows,
      List<Trace> traces,
      List<String> warnings,
      List<Scope> scope) {}

  public record Result(
      Mode mode,
      String answer,
      List<Map<String, Object>> rows,
      List<Source> sources,
      List<Trace> traces,
      List<String> warnings,
      Plan plan,
      boolean cached,
      String generatedAt,
      long durationMs,
      String answerMethod) {}
}
