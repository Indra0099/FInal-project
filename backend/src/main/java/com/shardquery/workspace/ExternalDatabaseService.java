package com.shardquery.workspace;

import static com.shardquery.workspace.WorkspaceTypes.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardquery.persistence.*;
import com.shardquery.web.ApiException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExternalDatabaseService {
  private final UserDatabaseRepository databases;
  private final UserTableRepository tables;
  private final ObjectMapper mapper;
  private final ConnectionSecrets secrets;
  private final Set<String> allowed;
  private final TransactionTemplate tx;

  public ExternalDatabaseService(
      UserDatabaseRepository databases,
      UserTableRepository tables,
      ObjectMapper mapper,
      ConnectionSecrets secrets,
      PlatformTransactionManager manager,
      @Value("${app.external-db-allowed-hosts}") String hosts) {
    this.databases = databases;
    this.tables = tables;
    this.mapper = mapper;
    this.secrets = secrets;
    this.tx = new TransactionTemplate(manager);
    this.allowed = new HashSet<>();
    for (String host : hosts.split(","))
      if (!host.isBlank()) allowed.add(host.strip().toLowerCase(Locale.ROOT));
  }

  public synchronized UUID save(UUID owner, UUID id, ConnectDatabase request) {
    String name = request.name().strip();
    UserDatabase d = id == null ? new UserDatabase() : owned(owner, id);
    if (id != null && !"EXTERNAL".equals(d.kind))
      throw new ApiException(409, "This is a local database, not a connection.");
    if (id == null
        && databases.findByUserIdOrderByCreatedAtAsc(owner).stream()
                .filter(x -> "EXTERNAL".equals(x.kind))
                .count()
            >= 5) throw new ApiException(409, "Up to five connections are supported per account.");
    if ((id == null || !name.equals(d.name)) && databases.existsByUserIdAndName(owner, name))
      throw new ApiException(409, "A database with that name already exists.");
    var spec = PostgresConnectionSpec.parse(request.connectionUrl(), allowed);
    if (id == null) {
      d.id = UUID.randomUUID();
      d.userId = owner;
      d.createdAt = Instant.now();
      d.physicalName = "remote_" + d.id.toString().replace("-", "");
    }
    validateSchema(request.schema());
    var discovered = discover(spec, request.schema());
    d.name = name;
    d.kind = "EXTERNAL";
    d.status = "READY";
    d.remoteSchema = request.schema();
    d.connectionSecret = secrets.encrypt(d.id, request.connectionUrl());
    d.connectionInfo = discovered.notice();
    tx.executeWithoutResult(
        status -> {
          databases.saveAndFlush(d);
          sync(d, discovered.tables());
        });
    return d.id;
  }

  public synchronized void refresh(UUID owner, UUID id) {
    var d = owned(owner, id);
    if (!"EXTERNAL".equals(d.kind))
      throw new ApiException(409, "Refresh schema applies to connected databases.");
    var discovered =
        discover(
            PostgresConnectionSpec.parse(secrets.decrypt(d.id, d.connectionSecret), allowed),
            d.remoteSchema);
    d.connectionInfo = discovered.notice();
    tx.executeWithoutResult(
        status -> {
          sync(d, discovered.tables());
          databases.save(d);
        });
  }

  private UserDatabase owned(UUID owner, UUID id) {
    return databases
        .findByIdAndUserId(id, owner)
        .orElseThrow(() -> new ApiException(404, "Database not found."));
  }

  static void validateSchema(String schema) {
    if (schema == null
        || !schema.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")
        || schema.startsWith("pg_")
        || Set.of("information_schema", "app").contains(schema.toLowerCase(Locale.ROOT)))
      throw new ApiException(
          400, "Choose an application data schema, not a system or ShardQuery metadata schema.");
  }

  public Connection connect(UserDatabase d) throws SQLException {
    var spec = PostgresConnectionSpec.parse(secrets.decrypt(d.id, d.connectionSecret), allowed);
    return connect(spec);
  }

  private Connection connect(PostgresConnectionSpec s) throws SQLException {
    var p = new Properties();
    p.setProperty("user", s.user());
    p.setProperty("password", s.password());
    p.setProperty("sslmode", s.sslmode());
    if (s.sslmode().equals("verify-full"))
      p.setProperty("sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory");
    p.setProperty("connectTimeout", "5");
    p.setProperty("socketTimeout", "12");
    p.setProperty("loginTimeout", "7");
    p.setProperty("readOnly", "true");
    p.setProperty(
        "options",
        "-c default_transaction_read_only=on -c statement_timeout=4000 -c lock_timeout=2000");
    var c = DriverManager.getConnection(s.jdbcUrl(), p);
    c.setReadOnly(true);
    c.setAutoCommit(false);
    return c;
  }

  record RemoteTable(String name, List<Column> columns) {}

  record Discovery(List<RemoteTable> tables, String notice) {}

  private Discovery discover(PostgresConnectionSpec spec, String schema) {
    var result = new ArrayList<RemoteTable>();
    int omitted = 0;
    try (var c = connect(spec)) {
      // Only base/partitioned tables: no views, foreign tables or model-supplied identifiers.
      var names = new ArrayList<String>();
      try (var s =
          c.prepareStatement(
              "SELECT c.relname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON"
                  + " n.oid=c.relnamespace WHERE n.nspname=? AND c.relkind IN ('r','p') AND NOT"
                  + " c.relispartition AND has_table_privilege(c.oid,'SELECT') ORDER BY c.relname"
                  + " LIMIT 51")) {
        s.setString(1, schema);
        s.setQueryTimeout(4);
        try (var r = s.executeQuery()) {
          while (r.next()) names.add(r.getString(1));
        }
      }
      if (names.size() > 50)
        throw new ApiException(
            422, "This connection supports up to 50 tables. Select a smaller schema.");
      for (String name : names) {
        var columns = new ArrayList<Column>();
        try (var s =
            c.prepareStatement(
                "SELECT column_name,data_type,is_nullable FROM information_schema.columns WHERE"
                    + " table_schema=? AND table_name=? ORDER BY ordinal_position")) {
          s.setString(1, schema);
          s.setString(2, name);
          s.setQueryTimeout(4);
          try (var r = s.executeQuery()) {
            while (r.next()) {
              FieldType type =
                  switch (r.getString(2)) {
                    case "smallint", "integer", "bigint" -> FieldType.INTEGER;
                    case "numeric", "decimal", "real", "double precision" -> FieldType.DECIMAL;
                    case "boolean" -> FieldType.BOOLEAN;
                    case "date" -> FieldType.DATE;
                    case "character varying", "character", "text", "uuid" -> FieldType.TEXT;
                    default -> null;
                  };
              if (type == null) {
                omitted++;
                continue;
              }
              columns.add(new Column(r.getString(1), type, "NO".equals(r.getString(3))));
            }
          }
        }
        if (columns.size() > 100)
          throw new ApiException(
              422, "A connected table exceeds 100 supported columns. Select a smaller schema.");
        if (!columns.isEmpty()) result.add(new RemoteTable(name, columns));
      }
      c.commit();
      if (result.isEmpty())
        throw new ApiException(
            422,
            "No readable tables with supported columns were found. Check the schema and SELECT"
                + " grants.");
      return new Discovery(
          result,
          omitted == 0
              ? "Read-only connection. Schema discovered successfully."
              : omitted
                  + " unsupported columns excluded (for example JSON, arrays, binary or"
                  + " timestamps). Refresh after schema changes.");
    } catch (SQLException e) {
      throw new ApiException(
          503,
          "Could not read the PostgreSQL database. Check the host, port, credentials, TLS settings,"
              + " schema and SELECT grants. Credentials have not been changed.");
    }
  }

  private void sync(UserDatabase d, List<RemoteTable> found) {
    var old = tables.findByDatabaseIdOrderByCreatedAtAsc(d.id);
    var retained = new HashSet<UUID>();
    for (var f : found) {
      var t =
          old.stream()
              .filter(
                  x ->
                      Objects.equals(x.remoteSchema, d.remoteSchema)
                          && x.physicalName.equals(f.name()))
              .findFirst()
              .orElseGet(UserTable::new);
      if (t.id == null) {
        t.id = UUID.randomUUID();
        t.databaseId = d.id;
        t.createdAt = Instant.now();
      }
      t.physicalName = f.name();
      t.name = f.name();
      t.remoteSchema = d.remoteSchema;
      try {
        t.columnsJson = mapper.writeValueAsString(f.columns());
      } catch (Exception e) {
        throw new IllegalStateException("Could not save schema metadata.");
      }
      tables.save(t);
      retained.add(t.id);
    }
    tables.deleteAll(old.stream().filter(t -> !retained.contains(t.id)).toList());
  }

  public static String quoted(String name) {
    if (name == null || name.isEmpty() || name.indexOf('\0') >= 0 || name.length() > 63)
      throw new ApiException(400, "Invalid discovered identifier.");
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }
}
