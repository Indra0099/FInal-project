package com.shardquery.workspace;

import static com.shardquery.workspace.WorkspaceTypes.*;

import com.shardquery.web.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Validates model plans against the owner's catalog, then joins and filters bounded real rows. */
public final class PersonalQueryEngine {
  private PersonalQueryEngine() {}

  public record Table(
      UUID id, UUID databaseId, String databaseName, String name, List<Column> columns) {}

  public record Filter(UUID tableId, String column, String operator, String value) {}

  public record Join(UUID leftTableId, String leftColumn, UUID rightTableId, String rightColumn) {}

  public record Sort(UUID tableId, String column, boolean descending) {}

  public record Plan(
      String target,
      List<UUID> tables,
      List<Join> joins,
      List<Filter> filters,
      Sort orderBy,
      int limit,
      String explanation) {}

  public record Checked(Plan plan, Map<UUID, Table> catalog) {}

  public static Checked validate(Plan plan, List<Table> owned) {
    if (plan == null
        || !"PERSONAL".equals(plan.target())
        || plan.tables() == null
        || plan.tables().isEmpty()
        || plan.tables().size() > 2
        || new HashSet<>(plan.tables()).size() != plan.tables().size()
        || plan.joins() == null
        || plan.filters() == null
        || plan.filters().size() > 5
        || plan.limit() < 1
        || plan.limit() > 100)
      throw invalid(
          "Choose one or two personal tables with up to five filters and at most 100 results.");
    var catalog = new LinkedHashMap<UUID, Table>();
    owned.forEach(t -> catalog.put(t.id(), t));
    for (var id : plan.tables())
      if (id == null || !catalog.containsKey(id))
        throw invalid("The plan referenced a table that is not available in your account.");
    if (plan.joins().size() != plan.tables().size() - 1)
      throw invalid("Two tables require one explicit matching-column relationship.");
    for (var join : plan.joins()) {
      if (join == null || Objects.equals(join.leftTableId(), join.rightTableId()))
        throw invalid("Invalid table relationship.");
      var left = column(plan, catalog, join.leftTableId(), join.leftColumn());
      var right = column(plan, catalog, join.rightTableId(), join.rightColumn());
      if (left.type() != right.type() && !(numeric(left.type()) && numeric(right.type())))
        throw invalid("The selected join columns have incompatible types.");
    }
    for (var filter : plan.filters()) {
      if (filter == null || filter.value() == null || filter.value().length() > 4000)
        throw invalid("Invalid filter value.");
      var col = column(plan, catalog, filter.tableId(), filter.column());
      if (!Set.of("EQ", "GT", "GTE", "LT", "LTE", "CONTAINS")
          .contains(Objects.toString(filter.operator(), "")))
        throw invalid("Unsupported filter operation.");
      if (filter.operator().equals("CONTAINS") && col.type() != FieldType.TEXT)
        throw invalid("Contains requires a text field.");
      if (col.type() == FieldType.BOOLEAN && !filter.operator().equals("EQ"))
        throw invalid("Boolean fields support equality filters.");
      if (WorkspaceService.convert(new Column(col.name(), col.type(), true), filter.value())
          == null) throw invalid("Empty-value filters are not supported.");
    }
    if (plan.orderBy() != null)
      column(plan, catalog, plan.orderBy().tableId(), plan.orderBy().column());
    return new Checked(plan, Collections.unmodifiableMap(catalog));
  }

  private static Column column(Plan p, Map<UUID, Table> catalog, UUID table, String name) {
    if (table == null || !p.tables().contains(table) || !catalog.containsKey(table))
      throw invalid("A column referenced a table outside this query.");
    return catalog.get(table).columns().stream()
        .filter(c -> c.name().equals(name))
        .findFirst()
        .orElseThrow(() -> invalid("Unknown column in the generated plan."));
  }

  public static List<Map<String, Object>> evaluate(
      Checked checked, Map<UUID, List<Map<String, Object>>> data) {
    var p = checked.plan();
    var catalog = checked.catalog();
    for (var id : p.tables())
      if (!data.containsKey(id) || data.get(id).size() > 1000)
        throw invalid(
            "A selected table exceeded the supported 1,000-row read limit. No incomplete answer was"
                + " generated.");
    List<Map<UUID, Map<String, Object>>> joined = new ArrayList<>();
    if (p.tables().size() == 1) {
      UUID id = p.tables().getFirst();
      for (var row : data.get(id)) joined.add(Map.of(id, row));
    } else {
      var join = p.joins().getFirst();
      var left = column(p, catalog, join.leftTableId(), join.leftColumn());
      var right = column(p, catalog, join.rightTableId(), join.rightColumn());
      Map<Object, List<Map<String, Object>>> index = new HashMap<>();
      for (var row : data.get(join.rightTableId())) {
        var key = key(right, row.get(right.name()));
        if (key != null) index.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
      }
      for (var row : data.get(join.leftTableId())) {
        var key = key(left, row.get(left.name()));
        if (key == null) continue;
        for (var match : index.getOrDefault(key, List.of())) {
          if (joined.size() >= 5000)
            throw invalid(
                "The relationship produced more than 5,000 joined rows. Use a unique identifier"
                    + " relationship or query a single table.");
          joined.add(Map.of(join.leftTableId(), row, join.rightTableId(), match));
        }
      }
    }
    var filtered =
        joined.stream()
            .filter(
                row ->
                    p.filters().stream()
                        .allMatch(
                            f ->
                                matches(
                                    column(p, catalog, f.tableId(), f.column()),
                                    row.get(f.tableId()).get(f.column()),
                                    f)))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    if (p.orderBy() != null) {
      var sort = p.orderBy();
      var col = column(p, catalog, sort.tableId(), sort.column());
      filtered.sort(
          (a, b) -> {
            Object x = a.get(sort.tableId()).get(sort.column()),
                y = b.get(sort.tableId()).get(sort.column());
            if (x == null) return y == null ? 0 : 1;
            if (y == null) return -1;
            int c = compare(col, x, y);
            return sort.descending() ? -c : c;
          });
    }
    var result = new ArrayList<Map<String, Object>>();
    for (var row : filtered.subList(0, Math.min(p.limit(), filtered.size()))) {
      var out = new LinkedHashMap<String, Object>();
      for (var id : p.tables())
        for (var entry : row.get(id).entrySet()) {
          var table = catalog.get(id);
          String label =
              p.tables().size() == 1
                  ? entry.getKey()
                  : table.databaseName() + " / " + table.name() + "." + entry.getKey();
          out.put(label, entry.getValue());
        }
      result.add(out);
    }
    return result;
  }

  private static boolean matches(Column col, Object actual, Filter f) {
    if (actual == null) return false;
    if (f.operator().equals("CONTAINS"))
      return actual
          .toString()
          .toLowerCase(Locale.ROOT)
          .contains(f.value().toLowerCase(Locale.ROOT));
    int comparison = compare(col, actual, f.value());
    return switch (f.operator()) {
      case "EQ" -> comparison == 0;
      case "GT" -> comparison > 0;
      case "GTE" -> comparison >= 0;
      case "LT" -> comparison < 0;
      case "LTE" -> comparison <= 0;
      default -> false;
    };
  }

  private static int compare(Column col, Object a, Object b) {
    if (numeric(col.type()))
      return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
    if (col.type() == FieldType.DATE)
      return LocalDate.parse(a.toString()).compareTo(LocalDate.parse(b.toString()));
    if (col.type() == FieldType.BOOLEAN)
      return Boolean.compare(
          Boolean.parseBoolean(a.toString()), Boolean.parseBoolean(b.toString()));
    return a.toString().compareTo(b.toString());
  }

  private static Object key(Column col, Object value) {
    return value == null
        ? null
        : numeric(col.type())
            ? new BigDecimal(value.toString()).stripTrailingZeros()
            : value.toString();
  }

  private static boolean numeric(FieldType t) {
    return t == FieldType.INTEGER || t == FieldType.DECIMAL;
  }

  private static ApiException invalid(String message) {
    return new ApiException(422, message);
  }
}
