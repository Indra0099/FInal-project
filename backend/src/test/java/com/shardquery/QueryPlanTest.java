package com.shardquery;

import static com.shardquery.query.QueryTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import com.shardquery.infrastructure.ShardManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QueryPlanTest {
  @Test
  void routesToStableLogicalBuckets() {
    assertEquals(2, ShardManager.bucket(101));
    assertEquals(0, ShardManager.bucket(102));
    assertEquals(1, ShardManager.bucket(103));
    assertThrows(IllegalArgumentException.class, () -> ShardManager.bucket(0));
  }

  @Test
  void boundsWorkAndRejectsUnsupportedQueries() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Plan(Mode.SQL, Intent.PRODUCTS, null, null, null, null, 101, "").validated());
    assertThrows(
        IllegalArgumentException.class,
        () -> new Plan(Mode.SQL, Intent.UNSUPPORTED, null, null, null, null, 50, "").validated());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Plan(Mode.SQL, Intent.PURCHASES, null, new BigDecimal("-1"), null, null, 50, "")
                .validated());
  }

  @Test
  void doesNotSilentlyIgnoreUnsupportedFilters() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Plan(Mode.SQL, Intent.CUSTOMERS, "laptop", null, null, null, 50, "").validated());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Plan(Mode.SQL, Intent.REVIEWS, null, BigDecimal.TEN, null, null, 50, "")
                .validated());
  }
}
