package com.shardquery.query;

import static com.shardquery.query.QueryTypes.*;

import com.shardquery.infrastructure.ShardManager;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class SqlEngine {
  private final ShardManager shards;

  public SqlEngine(ShardManager shards) {
    this.shards = shards;
  }

  public DatabaseResult execute(Plan plan) {
    plan.validated();
    var traces = new ArrayList<Trace>();
    var warnings = new ArrayList<String>();
    var scope = new ArrayList<Scope>();
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("cap", plan.limit() + 1);
    if (plan.customerId() != null) {
      p.put("customer", plan.customerId());
      p.put("bucket", ShardManager.bucket(plan.customerId()));
    }
    if (plan.productId() != null) p.put("product", plan.productId());
    if (plan.category() != null && !plan.category().isBlank())
      p.put("category", plan.category().toLowerCase(Locale.ROOT));
    if (plan.minPrice() != null) p.put("price", plan.minPrice());
    List<Map<String, Object>> rows;
    switch (plan.intent()) {
      case CUSTOMERS ->
          rows =
              run(
                  "customer",
                  "SELECT customer_id,name,email,city,bucket FROM customers WHERE 1=1"
                      + (p.containsKey("customer")
                          ? " AND customer_id=:customer AND bucket=:bucket"
                          : "")
                      + " ORDER BY customer_id LIMIT :cap",
                  p,
                  traces,
                  warnings);
      case PRODUCTS, INVENTORY -> {
        String cols =
            plan.intent() == Intent.INVENTORY
                ? "p.product_id,p.name,p.category,p.price,i.available,i.warehouse"
                : "p.product_id,p.name,p.category,p.price";
        rows =
            run(
                "product",
                "SELECT "
                    + cols
                    + " FROM products p"
                    + (plan.intent() == Intent.INVENTORY
                        ? " JOIN inventory i USING(product_id)"
                        : "")
                    + " WHERE 1=1"
                    + productFilter(p)
                    + (p.containsKey("price") ? " AND p.price > :price" : "")
                    + " ORDER BY p.product_id LIMIT :cap",
                p,
                traces,
                warnings);
      }
      case REVIEWS -> {
        rows =
            run(
                "product",
                "SELECT r.review_id,r.customer_id,p.product_id,p.name,r.rating,r.review_text FROM"
                    + " customer_reviews r JOIN products p USING(product_id) WHERE 1=1"
                    + productFilter(p)
                    + (p.containsKey("customer") ? " AND r.customer_id=:customer" : "")
                    + " ORDER BY r.review_id LIMIT :cap",
                p,
                traces,
                warnings);
      }
      case PURCHASES -> {
        var pp = new LinkedHashMap<>(p);
        pp.put("cap", 201);
        var products =
            run(
                "product",
                "SELECT p.product_id,p.name,p.category FROM products p WHERE 1=1"
                    + productFilter(p)
                    + " ORDER BY p.product_id LIMIT :cap",
                pp,
                traces,
                warnings);
        if (products.size() > 200) {
          products = new ArrayList<>(products.subList(0, 200));
          warnings.add("Product lookup limited to 200 products; associations may be incomplete.");
        }
        if (products.isEmpty()) return new DatabaseResult(List.of(), traces, warnings, scope);
        p.put("products", products.stream().map(x -> x.get("product_id")).toList());
        rows =
            run(
                "order",
                "SELECT"
                    + " o.order_id,o.customer_id,i.product_id,i.quantity,i.unit_price,o.status,o.ordered_at"
                    + " FROM orders o JOIN order_items i ON i.order_id=o.order_id AND"
                    + " i.bucket=o.bucket WHERE o.status='DELIVERED' AND i.product_id IN"
                    + " (:products)"
                    + (p.containsKey("price") ? " AND i.unit_price > :price" : "")
                    + (p.containsKey("customer")
                        ? " AND o.customer_id=:customer AND o.bucket=:bucket"
                        : "")
                    + " ORDER BY o.order_id,i.item_id LIMIT :cap",
                p,
                traces,
                warnings);
        if (!rows.isEmpty()) {
          var cp = new LinkedHashMap<String, Object>();
          cp.put("customers", rows.stream().map(x -> x.get("customer_id")).distinct().toList());
          String bucketClause = "";
          if (plan.customerId() != null) {
            cp.put("bucket", ShardManager.bucket(plan.customerId()));
            bucketClause = " AND bucket=:bucket";
          }
          var customers =
              run(
                  "customer",
                  "SELECT customer_id,name,city FROM customers WHERE customer_id IN (:customers)"
                      + bucketClause,
                  cp,
                  traces,
                  warnings);
          var names = index(customers, "customer_id");
          var catalog = index(products, "product_id");
          for (var row : rows) {
            var customer = names.get(asLong(row.get("customer_id")));
            var product = catalog.get(asLong(row.get("product_id")));
            row.put("customer_name", customer == null ? null : customer.get("name"));
            row.put("city", customer == null ? null : customer.get("city"));
            row.put("product_name", product == null ? null : product.get("name"));
            if (customer == null && warnings.isEmpty())
              warnings.add(
                  "A customer record is missing; the cross-shard association is incomplete.");
          }
        }
      }
      default -> throw new IllegalArgumentException("Unsupported SQL intent");
    }
    if (rows.size() > plan.limit()) {
      rows = new ArrayList<>(rows.subList(0, plan.limit()));
      warnings.add(
          "Results limited to "
              + plan.limit()
              + " records. Narrow the question for a complete selection.");
    }
    for (var row : rows)
      if (row.get("product_id") != null)
        scope.add(
            new Scope(
                asLong(row.get("product_id")),
                row.get("customer_id") == null ? 0 : asLong(row.get("customer_id"))));
    return new DatabaseResult(rows, traces, warnings, scope.stream().distinct().toList());
  }

  private static String productFilter(Map<String, Object> p) {
    return (p.containsKey("category") ? " AND lower(p.category)=:category" : "")
        + (p.containsKey("product") ? " AND p.product_id=:product" : "");
  }

  private List<Map<String, Object>> run(
      String shard,
      String sql,
      Map<String, Object> params,
      List<Trace> traces,
      List<String> warnings) {
    long start = System.nanoTime();
    try {
      var rows = shards.jdbc(shard).queryForList(sql, params);
      traces.add(
          new Trace(
              shard,
              (Integer) params.get("bucket"),
              sql,
              new LinkedHashMap<>(params),
              rows.size(),
              (System.nanoTime() - start) / 1000000,
              "COMPLETE"));
      return new ArrayList<>(rows);
    } catch (org.springframework.dao.DataAccessException e) {
      traces.add(
          new Trace(
              shard,
              (Integer) params.get("bucket"),
              sql,
              new LinkedHashMap<>(params),
              0,
              (System.nanoTime() - start) / 1000000,
              "UNAVAILABLE"));
      warnings.add(
          "The " + shard + " shard is unavailable or timed out. These results are incomplete.");
      return new ArrayList<>();
    }
  }

  private static long asLong(Object n) {
    return ((Number) n).longValue();
  }

  private static Map<Long, Map<String, Object>> index(List<Map<String, Object>> rows, String key) {
    var map = new HashMap<Long, Map<String, Object>>();
    rows.forEach(row -> map.put(asLong(row.get(key)), row));
    return map;
  }
}
