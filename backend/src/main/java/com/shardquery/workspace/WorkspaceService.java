package com.shardquery.workspace;

import static com.shardquery.workspace.WorkspaceTypes.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardquery.ai.GeminiGateway;
import com.shardquery.persistence.*;
import com.shardquery.query.QueryTypes.*;
import com.shardquery.web.ApiException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {
  private final UserDatabaseRepository databases;
  private final UserTableRepository tables;
  private final ObjectMapper mapper;
  private final String host, port, password;
  private final GeminiGateway ai;
  private final ExternalDatabaseService external;

  public WorkspaceService(
      UserDatabaseRepository databases,
      UserTableRepository tables,
      ObjectMapper mapper,
      GeminiGateway ai,
      ExternalDatabaseService external,
      @Value("${app.workspace-host}") String host,
      @Value("${app.workspace-port}") String port,
      @Value("${app.workspace-password}") String password) {
    this.databases = databases;
    this.tables = tables;
    this.mapper = mapper;
    this.ai = ai;
    this.external = external;
    this.host = host;
    this.port = port;
    this.password = password;
  }

  public List<Map<String, Object>> list(UUID owner) {
    return databases.findByUserIdOrderByCreatedAtAsc(owner).stream()
        .map(this::databaseView)
        .toList();
  }

  public UserDatabase ownedDatabase(UUID owner, UUID id) {
    return databases
        .findByIdAndUserId(id, owner)
        .orElseThrow(() -> new ApiException(404, "Database not found."));
  }

  UserTable ownedTable(UUID owner, UUID database, UUID table) {
    ownedDatabase(owner, database);
    return tables
        .findByIdAndDatabaseId(table, database)
        .orElseThrow(() -> new ApiException(404, "Table not found."));
  }

  Map<String, Object> databaseView(UserDatabase d) {
    return Map.of(
        "id",
        d.id,
        "name",
        d.name,
        "status",
        d.status,
        "kind",
        d.kind,
        "connectionInfo",
        Objects.toString(d.connectionInfo, ""),
        "schema",
        Objects.toString(d.remoteSchema, ""),
        "createdAt",
        d.createdAt,
        "tableCount",
        tables.countByDatabaseId(d.id));
  }

  public synchronized Map<String, Object> createDatabase(UUID owner, CreateDatabase request) {
    String name = request.name().strip();
    if (databases.findByUserIdOrderByCreatedAtAsc(owner).stream()
            .filter(d -> !"EXTERNAL".equals(d.kind))
            .count()
        >= 3)
      throw new ApiException(
          409, "Each account can create up to three databases in this local demonstration.");
    if (databases.existsByUserIdAndName(owner, name))
      throw new ApiException(409, "A database with that name already exists.");
    var d = new UserDatabase();
    d.id = UUID.randomUUID();
    d.userId = owner;
    d.name = name;
    d.physicalName = "sq_" + d.id.toString().replace("-", "");
    d.status = "CREATING";
    d.createdAt = Instant.now();
    databases.saveAndFlush(d);
    return provision(d);
  }

  public synchronized Map<String, Object> retryDatabase(UUID owner, UUID id) {
    var d = ownedDatabase(owner, id);
    requireLocal(d);
    if ("READY".equals(d.status)) return databaseView(d);
    return provision(d);
  }

  private Map<String, Object> provision(UserDatabase d) {
    try (var c = connect("shard_product_db")) {
      boolean exists;
      try (var s = c.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
        s.setString(1, d.physicalName);
        try (var r = s.executeQuery()) {
          exists = r.next();
        }
      }
      if (!exists)
        try (var s = c.createStatement()) {
          s.setQueryTimeout(15);
          s.execute("CREATE DATABASE " + identifier(d.physicalName) + " TEMPLATE template0");
        }
      try (var s = c.createStatement()) {
        s.execute("REVOKE CONNECT ON DATABASE " + identifier(d.physicalName) + " FROM PUBLIC");
      }
      d.status = "READY";
      databases.saveAndFlush(d);
      return databaseView(d);
    } catch (SQLException e) {
      d.status = "FAILED";
      databases.save(d);
      throw new ApiException(
          503,
          "Database creation is unavailable. Check the workspace database role in the setup guide,"
              + " then retry.");
    }
  }

  public List<Map<String, Object>> tables(UUID owner, UUID database) {
    var d = ownedDatabase(owner, database);
    return tables.findByDatabaseIdOrderByCreatedAtAsc(database).stream()
        .map(t -> tableView(d, t))
        .toList();
  }

  public List<Map<String, Object>> allTables(UUID owner) {
    var out = new ArrayList<Map<String, Object>>();
    for (var d : databases.findByUserIdOrderByCreatedAtAsc(owner))
      for (var t : tables.findByDatabaseIdOrderByCreatedAtAsc(d.id)) out.add(tableView(d, t));
    return out;
  }

  private Map<String, Object> tableView(UserDatabase d, UserTable t) {
    return Map.of(
        "id",
        t.id,
        "databaseId",
        d.id,
        "databaseName",
        d.name,
        "name",
        t.name,
        "columns",
        columns(t),
        "readOnly",
        "EXTERNAL".equals(d.kind),
        "createdAt",
        t.createdAt);
  }

  public synchronized Map<String, Object> createTable(
      UUID owner, UUID database, CreateTable request) throws Exception {
    var d = ownedDatabase(owner, database);
    requireLocal(d);
    if (!"READY".equals(d.status)) throw new ApiException(409, "Wait until the database is ready.");
    if (tables.countByDatabaseId(database) >= 12)
      throw new ApiException(409, "Each database supports up to 12 tables in this demonstration.");
    if (tables.existsByDatabaseIdAndName(database, request.name().strip()))
      throw new ApiException(409, "A table with that name already exists.");
    if (new HashSet<>(request.columns().stream().map(Column::name).toList()).size()
        != request.columns().size()) throw new ApiException(400, "Column names must be unique.");
    var t = new UserTable();
    t.id = UUID.randomUUID();
    t.databaseId = d.id;
    t.name = request.name().strip();
    t.physicalName = "t_" + t.id.toString().replace("-", "");
    t.columnsJson = mapper.writeValueAsString(request.columns());
    t.createdAt = Instant.now();
    String sql =
        "CREATE TABLE "
            + identifier(t.physicalName)
            + " (_row_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
    for (var col : request.columns())
      sql +=
          ", "
              + identifier(col.name())
              + " "
              + sqlType(col.type())
              + (col.required() ? " NOT NULL" : "");
    sql += ")";
    try (var c = open(d)) {
      c.setAutoCommit(false);
      try (var stmt = c.createStatement()) {
        stmt.setQueryTimeout(8);
        stmt.execute(sql);
        tables.saveAndFlush(t);
        c.commit();
      } catch (Exception e) {
        c.rollback();
        tables.deleteById(t.id);
        throw e;
      }
    } catch (SQLException e) {
      throw new ApiException(
          503, "Table creation failed. Please retry when the database is available.");
    }
    return tableView(d, t);
  }

  public Map<String, Object> rows(UUID owner, UUID database, UUID table, int page)
      throws Exception {
    if (page < 0 || page > 10000) throw new ApiException(400, "Invalid page number.");
    var d = ownedDatabase(owner, database);
    var t = ownedTable(owner, database, table);
    if ("EXTERNAL".equals(d.kind)) {
      if (page > 19)
        throw new ApiException(400, "Connected-table preview is limited to 1,000 rows.");
      try (var c = open(d);
          var stmt =
              c.prepareStatement(
                  "SELECT "
                      + selection(t)
                      + " FROM "
                      + relation(t)
                      + " ORDER BY "
                      + ExternalDatabaseService.quoted(columns(t).getFirst().name())
                      + " LIMIT 1001")) {
        stmt.setQueryTimeout(4);
        var all = readRows(stmt);
        boolean truncated = all.size() > 1000;
        int total = Math.min(all.size(), 1000);
        c.commit();
        return Map.of(
            "rows",
            all.subList(Math.min(page * 50, total), Math.min((page + 1) * 50, total)),
            "total",
            total,
            "page",
            page,
            "pageSize",
            50,
            "truncated",
            truncated);
      } catch (SQLException e) {
        throw new ApiException(
            503,
            "Connected table could not be read. Refresh its schema and check connection access.");
      }
    }
    try (var c = open(d)) {
      long count;
      try (var s = c.createStatement()) {
        s.setQueryTimeout(4);
        try (var r = s.executeQuery("SELECT count(*) FROM " + identifier(t.physicalName))) {
          r.next();
          count = r.getLong(1);
        }
      }
      try (var s =
          c.prepareStatement(
              "SELECT * FROM "
                  + identifier(t.physicalName)
                  + " ORDER BY _row_id LIMIT 50 OFFSET ?")) {
        s.setQueryTimeout(4);
        s.setInt(1, page * 50);
        return Map.of("rows", readRows(s), "total", count, "page", page, "pageSize", 50);
      }
    } catch (SQLException e) {
      throw new ApiException(503, "The selected table is unavailable. Please try again.");
    }
  }

  public synchronized Map<String, Object> saveRow(
      UUID owner, UUID database, UUID table, Long rowId, Row request) throws Exception {
    var d = ownedDatabase(owner, database);
    var t = ownedTable(owner, database, table);
    requireLocal(d);
    var columns = columns(t);
    var names = columns.stream().map(Column::name).toList();
    if (!new HashSet<>(names).containsAll(request.values().keySet()))
      throw new ApiException(400, "The row contains an unknown column.");
    var values = new ArrayList<Object>();
    for (var col : columns) values.add(convert(col, request.values().get(col.name())));
    String sql =
        rowId == null
            ? "INSERT INTO "
                + identifier(t.physicalName)
                + " ("
                + String.join(",", names.stream().map(WorkspaceService::identifier).toList())
                + ") VALUES ("
                + String.join(",", Collections.nCopies(names.size(), "?"))
                + ") RETURNING *"
            : "UPDATE "
                + identifier(t.physicalName)
                + " SET "
                + String.join(",", names.stream().map(n -> identifier(n) + "=?").toList())
                + " WHERE _row_id=? RETURNING *";
    try (var c = open(d)) {
      if (rowId == null)
        try (var s = c.createStatement()) {
          s.setQueryTimeout(4);
          try (var r = s.executeQuery("SELECT count(*) FROM " + identifier(t.physicalName))) {
            r.next();
            if (r.getLong(1) >= 1000)
              throw new ApiException(
                  409, "Each table is limited to 1,000 rows in this demonstration.");
          }
        }
      try (var s = c.prepareStatement(sql)) {
        s.setQueryTimeout(4);
        for (int i = 0; i < values.size(); i++) s.setObject(i + 1, values.get(i));
        if (rowId != null) s.setLong(values.size() + 1, rowId);
        var rows = readRows(s);
        if (rows.isEmpty()) throw new ApiException(404, "Row not found.");
        return rows.getFirst();
      }
    } catch (SQLException e) {
      throw new ApiException(
          400, "The row could not be saved. Check the field values and database availability.");
    }
  }

  public List<PersonalQueryEngine.Table> catalog(UUID owner) {
    var result = new ArrayList<PersonalQueryEngine.Table>();
    for (var d : databases.findByUserIdOrderByCreatedAtAsc(owner))
      for (var t : tables.findByDatabaseIdOrderByCreatedAtAsc(d.id))
        result.add(new PersonalQueryEngine.Table(t.id, d.id, d.name, t.name, columns(t)));
    return result;
  }

  public PersonalQueryEngine.Plan route(UUID owner, String question, Mode mode, DataSource source)
      throws Exception {
    var catalog = catalog(owner);
    if (catalog.isEmpty()) {
      if (source == DataSource.PERSONAL)
        throw new ApiException(
            422, "You have no personal tables yet. Create a table in My databases first.");
      return new PersonalQueryEngine.Plan(
          "DEMO", List.of(), List.of(), List.of(), null, 50, "Use sample commerce data.");
    }
    PersonalQueryEngine.Plan plan;
    try {
      plan =
          mapper.treeToValue(
              ai.workspacePlan(
                  question,
                  mapper.writeValueAsString(catalog),
                  mode.name(),
                  source == DataSource.PERSONAL),
              PersonalQueryEngine.Plan.class);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(
          502,
          "The AI returned an invalid data-source plan. Please retry with the database or table"
              + " name.");
    }
    if (plan == null
        || !Set.of("PERSONAL", "DEMO", "DOCUMENTS", "UNSUPPORTED")
            .contains(Objects.toString(plan.target(), "")))
      throw new ApiException(502, "The AI returned an invalid data source.");
    if (plan.target().equals("UNSUPPORTED")
        || source == DataSource.PERSONAL && !plan.target().equals("PERSONAL"))
      throw new ApiException(
          422,
          plan.explanation() == null
              ? "No supported personal-table query matched. Include the database and field names in"
                  + " your question."
              : plan.explanation());
    if (plan.target().equals("DOCUMENTS") && mode != Mode.AUTO)
      throw new ApiException(422, "Choose Documents or Auto mode to search uploaded files.");
    if (plan.target().equals("PERSONAL")) {
      if (mode == Mode.HYBRID)
        throw new ApiException(
            422,
            "Use Auto or Database mode for personal tables. Combining them with documents is not"
                + " supported yet.");
      PersonalQueryEngine.validate(plan, catalog);
    }
    return plan;
  }

  public PersonalResult executeAcross(UUID owner, PersonalQueryEngine.Plan plan) throws Exception {
    var checked = PersonalQueryEngine.validate(plan, catalog(owner));
    if (plan.tables().size() == 1) {
      var entry = checked.catalog().get(plan.tables().getFirst());
      var d = ownedDatabase(owner, entry.databaseId());
      var t = ownedTable(owner, d.id, entry.id());
      var json = mapper.createObjectNode();
      json.put("unsupported", false);
      json.put("limit", plan.limit());
      json.put("explanation", plan.explanation());
      if (plan.orderBy() == null) json.putNull("orderBy");
      else json.put("orderBy", plan.orderBy().column());
      json.put("descending", plan.orderBy() != null && plan.orderBy().descending());
      var filters = json.putArray("filters");
      for (var f : plan.filters()) {
        var node = filters.addObject();
        node.put("column", f.column());
        node.put("operator", f.operator());
        node.put("value", f.value());
      }
      return executePlan(d, t, json);
    }
    var rowsByTable = new LinkedHashMap<UUID, List<Map<String, Object>>>();
    var traces = new ArrayList<Trace>();
    for (var id : plan.tables()) {
      var entry = checked.catalog().get(id);
      var d = ownedDatabase(owner, entry.databaseId());
      var t = ownedTable(owner, d.id, id);
      String sql =
          "SELECT "
              + selection(t)
              + " FROM "
              + relation(t)
              + " ORDER BY "
              + defaultOrder(t)
              + " LIMIT ?";
      long start = System.nanoTime();
      try (var c = open(d)) {
        c.setReadOnly(true);
        c.setAutoCommit(false);
        try (var stmt = c.prepareStatement(sql)) {
          stmt.setQueryTimeout(4);
          stmt.setInt(1, 1001);
          var rows = readRows(stmt);
          c.commit();
          if (rows.size() > 1000)
            throw new ApiException(
                422,
                "A selected table exceeds the 1,000-row personal-query limit. No incomplete result"
                    + " was returned.");
          rowsByTable.put(id, rows);
          traces.add(
              new Trace(
                  d.name + " / " + t.name,
                  null,
                  sql,
                  Map.of("limit", 1001),
                  rows.size(),
                  (System.nanoTime() - start) / 1_000_000,
                  "COMPLETE"));
        }
      } catch (SQLException e) {
        throw new ApiException(
            503,
            "The selected personal database is unavailable. No sample-database result was"
                + " substituted.");
      }
    }
    var rows = PersonalQueryEngine.evaluate(checked, rowsByTable);
    return new PersonalResult(
        new DatabaseResult(rows, traces, List.of(), List.of()),
        plan.explanation()
            + " Records were read from the selected personal tables; filtering, association and"
            + " sorting ran in the application before the final result limit.",
        plan.limit());
  }

  public record PersonalResult(DatabaseResult data, String explanation, int limit) {}

  public PersonalResult query(UUID owner, UUID tableId, String question) throws Exception {
    var t = tables.findById(tableId).orElseThrow(() -> new ApiException(404, "Table not found."));
    var d = ownedDatabase(owner, t.databaseId);
    var plan = ai.tablePlan(question, t.columnsJson);
    return executePlan(d, t, plan);
  }

  public PersonalResult executePlan(
      UserDatabase d, UserTable t, com.fasterxml.jackson.databind.JsonNode plan) throws Exception {
    if (!plan.path("unsupported").isBoolean() || plan.path("unsupported").asBoolean())
      throw new ApiException(
          422,
          "This selected table supports filtering and sorting. Choose My databases to join two"
              + " related tables. Aggregates and data changes are not supported.");
    if (!plan.path("limit").isIntegralNumber()
        || !plan.path("limit").canConvertToInt()
        || plan.path("limit").asInt() < 1
        || plan.path("limit").asInt() > 100
        || !plan.path("filters").isArray()
        || plan.path("filters").size() > 5)
      throw new ApiException(422, "The generated table plan exceeded the query limits.");
    var fields = new HashMap<String, Column>();
    columns(t).forEach(c -> fields.put(c.name(), c));
    var clauses = new ArrayList<String>();
    var values = new ArrayList<Object>();
    var bound = new LinkedHashMap<String, Object>();
    for (var f : plan.path("filters")) {
      var column = fields.get(f.path("column").asText());
      if (column == null || !f.path("value").isTextual())
        throw new ApiException(
            422, "The generated plan contains an unknown column or invalid value.");
      String op = f.path("operator").asText();
      Object value = convert(column, f.get("value").asText());
      if (value == null) throw new ApiException(422, "Empty-value filters are not supported.");
      String comparison =
          switch (op) {
            case "EQ" -> "=";
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            case "LTE" -> "<=";
            case "CONTAINS" -> "ILIKE";
            default -> throw new ApiException(422, "Unsupported filter operation.");
          };
      if (op.equals("CONTAINS")) {
        if (column.type() != FieldType.TEXT)
          throw new ApiException(422, "Contains applies only to text fields.");
        value =
            "%" + value.toString().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
      }
      String field = ExternalDatabaseService.quoted(column.name());
      if (t.remoteSchema != null && column.type() == FieldType.TEXT)
        field = "CAST(" + field + " AS text)";
      clauses.add(field + " " + comparison + " ?" + (op.equals("CONTAINS") ? " ESCAPE '!'" : ""));
      bound.put("filter" + values.size(), value);
      values.add(value);
    }
    String order = t.remoteSchema == null ? "_row_id" : columns(t).getFirst().name();
    if (!plan.path("orderBy").isNull()) {
      order = plan.path("orderBy").asText();
      if (!fields.containsKey(order)) throw new ApiException(422, "Unknown sort column.");
    }
    String sql =
        "SELECT "
            + selection(t)
            + " FROM "
            + relation(t)
            + (clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses))
            + " ORDER BY "
            + ExternalDatabaseService.quoted(order)
            + (plan.path("descending").asBoolean() ? " DESC" : " ASC")
            + " LIMIT ?";
    bound.put("limit", plan.path("limit").asInt());
    values.add(plan.path("limit").asInt());
    long started = System.nanoTime();
    try (var c = open(d)) {
      c.setReadOnly(true);
      c.setAutoCommit(false);
      try (var stmt = c.prepareStatement(sql)) {
        stmt.setQueryTimeout(4);
        for (int i = 0; i < values.size(); i++) stmt.setObject(i + 1, values.get(i));
        var rows = readRows(stmt);
        c.commit();
        return new PersonalResult(
            new DatabaseResult(
                rows,
                List.of(
                    new Trace(
                        d.name + " / " + t.name,
                        null,
                        sql,
                        bound,
                        rows.size(),
                        (System.nanoTime() - started) / 1_000_000,
                        "COMPLETE")),
                List.of(),
                List.of()),
            plan.path("explanation").asText(),
            plan.path("limit").asInt());
      }
    } catch (SQLException e) {
      throw new ApiException(
          503,
          "The database query could not be completed. Check the selected table and try again.");
    }
  }

  public List<Column> columns(UserTable t) {
    try {
      return mapper.readValue(t.columnsJson, new TypeReference<List<Column>>() {});
    } catch (Exception e) {
      throw new ApiException(503, "Table metadata could not be read.");
    }
  }

  public static Object convert(Column column, Object value) {
    if (value == null || value instanceof String s && s.isEmpty()) {
      if (column.required()) throw new ApiException(400, column.name() + " is required.");
      return null;
    }
    if (!(value instanceof String || value instanceof Number || value instanceof Boolean))
      throw new ApiException(400, "Field values must be text, numbers, dates or booleans.");
    String text = value.toString();
    if (text.length() > 4000)
      throw new ApiException(400, "Field values must be at most 4,000 characters.");
    try {
      return switch (column.type()) {
        case TEXT -> text;
        case INTEGER -> Long.valueOf(text);
        case DECIMAL -> {
          var n = new BigDecimal(text);
          if (n.scale() > 4 || n.precision() - n.scale() > 14) throw new IllegalArgumentException();
          yield n;
        }
        case BOOLEAN -> {
          if (!text.equals("true") && !text.equals("false")) throw new IllegalArgumentException();
          yield Boolean.valueOf(text);
        }
        case DATE -> LocalDate.parse(text);
      };
    } catch (Exception e) {
      throw new ApiException(
          400,
          "Invalid "
              + column.type().name().toLowerCase(Locale.ROOT)
              + " value for "
              + column.name()
              + ".");
    }
  }

  public static String identifier(String name) {
    if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}"))
      throw new ApiException(400, "Invalid database identifier.");
    return "\"" + name + "\"";
  }

  static String sqlType(FieldType type) {
    return switch (type) {
      case TEXT -> "varchar(4000)";
      case INTEGER -> "bigint";
      case DECIMAL -> "numeric(18,4)";
      case BOOLEAN -> "boolean";
      case DATE -> "date";
    };
  }

  Connection connect(String database) throws SQLException {
    identifier(database);
    var properties = new Properties();
    properties.setProperty("user", "shard_workspace");
    properties.setProperty("password", password);
    properties.setProperty("connectTimeout", "5");
    properties.setProperty("socketTimeout", "20");
    return DriverManager.getConnection(
        "jdbc:postgresql://" + host + ":" + port + "/" + database, properties);
  }

  static void requireLocal(UserDatabase d) {
    if ("EXTERNAL".equals(d.kind))
      throw new ApiException(
          409,
          "Connected databases are read-only. Manage their data in the source database, then"
              + " refresh the schema here.");
  }

  private Connection open(UserDatabase d) throws SQLException {
    return "EXTERNAL".equals(d.kind) ? external.connect(d) : connect(d.physicalName);
  }

  private String selection(UserTable t) {
    if (t.remoteSchema == null) return "*";
    return String.join(
        ",", columns(t).stream().map(c -> ExternalDatabaseService.quoted(c.name())).toList());
  }

  private String relation(UserTable t) {
    return t.remoteSchema == null
        ? identifier(t.physicalName)
        : ExternalDatabaseService.quoted(t.remoteSchema)
            + "."
            + ExternalDatabaseService.quoted(t.physicalName);
  }

  private String defaultOrder(UserTable t) {
    return t.remoteSchema == null
        ? "_row_id"
        : ExternalDatabaseService.quoted(columns(t).getFirst().name());
  }

  private static List<Map<String, Object>> readRows(PreparedStatement s) throws SQLException {
    var rows = new ArrayList<Map<String, Object>>();
    try (var r = s.executeQuery()) {
      var meta = r.getMetaData();
      while (r.next()) {
        var row = new LinkedHashMap<String, Object>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
          Object value = r.getObject(i);
          row.put(
              meta.getColumnLabel(i),
              value instanceof java.sql.Date
                      || value instanceof BigDecimal
                      || value instanceof Long && !meta.getColumnLabel(i).equals("_row_id")
                  ? value.toString()
                  : value);
        }
        rows.add(row);
      }
    }
    return rows;
  }
}
