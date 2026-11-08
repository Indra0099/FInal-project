package com.shardquery;

import static com.shardquery.query.QueryTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shardquery.infrastructure.ShardManager;
import com.shardquery.query.SqlEngine;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SqlEngineTest {
  @Test
  void keepsUntrustedCategoryOutOfSql() {
    var shards = mock(ShardManager.class);
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    when(shards.jdbc("product")).thenReturn(jdbc);
    when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());
    String injection = "laptop'; DROP TABLE products; --";
    var result =
        new SqlEngine(shards)
            .execute(new Plan(Mode.SQL, Intent.PRODUCTS, injection, null, null, null, 50, ""));
    assertFalse(result.traces().getFirst().sql().contains(injection));
    assertEquals(injection.toLowerCase(), result.traces().getFirst().parameters().get("category"));
  }

  @Test
  void reportsShardFailureWithoutFabricatedRows() {
    var shards = mock(ShardManager.class);
    when(shards.jdbc("customer"))
        .thenThrow(
            new org.springframework.dao.DataAccessResourceFailureException("connection refused"));
    var result =
        new SqlEngine(shards)
            .execute(new Plan(Mode.SQL, Intent.CUSTOMERS, null, null, 101, null, 50, ""));
    assertTrue(result.rows().isEmpty());
    assertFalse(result.warnings().isEmpty());
    assertEquals("UNAVAILABLE", result.traces().getFirst().status());
  }

  @Test
  void truncationIsExplicit() {
    var shards = mock(ShardManager.class);
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    when(shards.jdbc("customer")).thenReturn(jdbc);
    when(jdbc.queryForList(anyString(), anyMap()))
        .thenReturn(List.of(Map.of("customer_id", 101), Map.of("customer_id", 102)));
    var result =
        new SqlEngine(shards)
            .execute(new Plan(Mode.SQL, Intent.CUSTOMERS, null, null, null, null, 1, ""));
    assertEquals(1, result.rows().size());
    assertFalse(result.warnings().isEmpty());
  }
}
