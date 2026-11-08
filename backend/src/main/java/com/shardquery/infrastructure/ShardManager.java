package com.shardquery.infrastructure;

import com.zaxxer.hikari.*;
import jakarta.annotation.PreDestroy;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShardManager {
  private final Map<String, HikariDataSource> pools = new LinkedHashMap<>();

  public ShardManager(
      @Value("${app.customer-url}") String customer,
      @Value("${app.order-url}") String order,
      @Value("${app.product-url}") String product,
      @Value("${app.reader-password}") String password) {
    Map.of("customer", customer, "order", order, "product", product)
        .forEach(
            (name, url) -> {
              HikariConfig c = new HikariConfig();
              c.setJdbcUrl(url);
              c.setUsername("query_reader");
              c.setPassword(password);
              c.setPoolName("shard-" + name);
              c.setMaximumPoolSize(4);
              c.setMinimumIdle(0);
              c.setConnectionTimeout(2500);
              c.setValidationTimeout(1500);
              c.setInitializationFailTimeout(-1);
              c.setReadOnly(true);
              c.addDataSourceProperty("socketTimeout", "6");
              c.addDataSourceProperty("connectTimeout", "3");
              pools.put(name, new HikariDataSource(c));
            });
  }

  public NamedParameterJdbcTemplate jdbc(String name) {
    if (!pools.containsKey(name)) throw new IllegalArgumentException("Unknown shard");
    var jdbc = new NamedParameterJdbcTemplate(pools.get(name));
    jdbc.getJdbcTemplate().setQueryTimeout(4);
    jdbc.getJdbcTemplate().setMaxRows(201);
    return jdbc;
  }

  public static int bucket(int customerId) {
    if (customerId < 1) throw new IllegalArgumentException("Positive customer ID required");
    return Math.floorMod(Integer.hashCode(customerId), 3);
  }

  public Map<String, String> health() {
    Map<String, String> h = new LinkedHashMap<>();
    pools.forEach(
        (name, pool) -> {
          try (var c = pool.getConnection()) {
            h.put(name, c.isValid(1) ? "UP" : "DOWN");
          } catch (Exception e) {
            h.put(name, "DOWN");
          }
        });
    return h;
  }

  @PreDestroy
  void close() {
    pools.values().forEach(HikariDataSource::close);
  }
}
