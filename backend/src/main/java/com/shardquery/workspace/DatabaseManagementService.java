package com.shardquery.workspace;

import static com.shardquery.workspace.WorkspaceTypes.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardquery.persistence.*;
import com.shardquery.web.ApiException;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DatabaseManagementService {
  private final WorkspaceService workspace;
  private final UserDatabaseRepository databases;
  private final UserTableRepository tables;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;

  public DatabaseManagementService(
      WorkspaceService workspace,
      UserDatabaseRepository databases,
      UserTableRepository tables,
      ObjectMapper mapper,
      PlatformTransactionManager manager) {
    this.workspace = workspace;
    this.databases = databases;
    this.tables = tables;
    this.mapper = mapper;
    tx = new TransactionTemplate(manager);
  }

  public synchronized void renameDatabase(UUID owner, UUID id, Rename request) {
    var d = workspace.ownedDatabase(owner, id);
    String name = request.name().strip();
    if (!name.equals(d.name) && databases.existsByUserIdAndName(owner, name))
      throw new ApiException(409, "A database with that name already exists.");
    d.name = name;
    databases.saveAndFlush(d);
  }

  public synchronized void deleteDatabase(UUID owner, UUID id, Confirmation request) {
    var d = workspace.ownedDatabase(owner, id);
    confirm(d.name, request);
    if (!"EXTERNAL".equals(d.kind)) {
      if (!d.physicalName.matches("sq_[0-9a-f]{32}"))
        throw new ApiException(409, "This database cannot be deleted from the workspace.");
      try (var c = workspace.connect("shard_product_db");
          var s = c.createStatement()) {
        s.setQueryTimeout(8);
        s.execute("DROP DATABASE IF EXISTS " + WorkspaceService.identifier(d.physicalName));
      } catch (SQLException e) {
        throw new ApiException(
            409,
            "Database is busy or unavailable. Wait for running queries to finish, then retry"
                + " deletion. No connections were forcibly closed.");
      }
    }
    tx.executeWithoutResult(
        status -> {
          tables.deleteAll(tables.findByDatabaseIdOrderByCreatedAtAsc(id));
          tables.flush();
          databases.delete(d);
        });
  }

  public synchronized void renameTable(UUID owner, UUID db, UUID table, Rename request) {
    var d = workspace.ownedDatabase(owner, db);
    WorkspaceService.requireLocal(d);
    var t = workspace.ownedTable(owner, db, table);
    String name = request.name().strip();
    if (!name.equals(t.name) && tables.existsByDatabaseIdAndName(db, name))
      throw new ApiException(409, "A table with that name already exists.");
    t.name = name;
    tables.saveAndFlush(t);
  }

  public synchronized void deleteTable(UUID owner, UUID db, UUID table, Confirmation request) {
    var d = workspace.ownedDatabase(owner, db);
    WorkspaceService.requireLocal(d);
    var t = workspace.ownedTable(owner, db, table);
    confirm(t.name, request);
    ddl(
        d,
        "DROP TABLE IF EXISTS " + WorkspaceService.identifier(t.physicalName),
        () -> tables.delete(t));
  }

  public synchronized void addColumn(UUID owner, UUID db, UUID table, Column column)
      throws Exception {
    var d = workspace.ownedDatabase(owner, db);
    WorkspaceService.requireLocal(d);
    var t = workspace.ownedTable(owner, db, table);
    var cols = new ArrayList<>(workspace.columns(t));
    if (cols.size() >= 20) throw new ApiException(409, "A local table supports up to 20 columns.");
    if (cols.stream().anyMatch(c -> c.name().equals(column.name())))
      throw new ApiException(409, "That column already exists.");
    cols.add(column);
    String updated = mapper.writeValueAsString(cols);
    ddl(
        d,
        "ALTER TABLE "
            + WorkspaceService.identifier(t.physicalName)
            + " ADD COLUMN "
            + WorkspaceService.identifier(column.name())
            + " "
            + WorkspaceService.sqlType(column.type())
            + (column.required() ? " NOT NULL" : ""),
        () -> {
          t.columnsJson = updated;
          tables.saveAndFlush(t);
        });
  }

  public synchronized void renameColumn(
      UUID owner, UUID db, UUID table, String column, RenameColumn request) throws Exception {
    var d = workspace.ownedDatabase(owner, db);
    WorkspaceService.requireLocal(d);
    var t = workspace.ownedTable(owner, db, table);
    var cols = new ArrayList<>(workspace.columns(t));
    int idx = index(cols, column);
    String name = request.name();
    if (name.equals(column)) return;
    if (cols.stream().anyMatch(c -> c.name().equals(name)))
      throw new ApiException(409, "That column already exists.");
    var old = cols.get(idx);
    cols.set(idx, new Column(name, old.type(), old.required()));
    String updated = mapper.writeValueAsString(cols);
    ddl(
        d,
        "ALTER TABLE "
            + WorkspaceService.identifier(t.physicalName)
            + " RENAME COLUMN "
            + WorkspaceService.identifier(column)
            + " TO "
            + WorkspaceService.identifier(name),
        () -> {
          t.columnsJson = updated;
          tables.saveAndFlush(t);
        });
  }

  public synchronized void deleteColumn(
      UUID owner, UUID db, UUID table, String column, Confirmation request) throws Exception {
    var d = workspace.ownedDatabase(owner, db);
    WorkspaceService.requireLocal(d);
    var t = workspace.ownedTable(owner, db, table);
    confirm(column, request);
    var cols = new ArrayList<>(workspace.columns(t));
    int idx = index(cols, column);
    if (cols.size() == 1)
      throw new ApiException(409, "Keep at least one data column, or delete the table instead.");
    cols.remove(idx);
    String updated = mapper.writeValueAsString(cols);
    ddl(
        d,
        "ALTER TABLE "
            + WorkspaceService.identifier(t.physicalName)
            + " DROP COLUMN "
            + WorkspaceService.identifier(column)
            + " RESTRICT",
        () -> {
          t.columnsJson = updated;
          tables.saveAndFlush(t);
        });
  }

  public synchronized void deleteRow(
      UUID owner, UUID db, UUID table, long row, Confirmation request) {
    var d = workspace.ownedDatabase(owner, db);
    WorkspaceService.requireLocal(d);
    var t = workspace.ownedTable(owner, db, table);
    confirm(Long.toString(row), request);
    try (var c = workspace.connect(d.physicalName);
        var s =
            c.prepareStatement(
                "DELETE FROM "
                    + WorkspaceService.identifier(t.physicalName)
                    + " WHERE _row_id=?")) {
      s.setQueryTimeout(4);
      s.setLong(1, row);
      if (s.executeUpdate() == 0) throw new ApiException(404, "Row not found.");
    } catch (SQLException e) {
      throw new ApiException(503, "The row could not be deleted. Check database availability.");
    }
  }

  private static int index(List<Column> cols, String name) {
    for (int i = 0; i < cols.size(); i++) if (cols.get(i).name().equals(name)) return i;
    throw new ApiException(404, "Column not found.");
  }

  private static void confirm(String expected, Confirmation supplied) {
    if (supplied == null || !expected.equals(supplied.confirmation()))
      throw new ApiException(
          409, "Confirmation does not match. Type the current name or row ID exactly.");
  }

  private void ddl(UserDatabase d, String sql, Runnable metadata) {
    try (var c = workspace.connect(d.physicalName)) {
      c.setAutoCommit(false);
      try (var s = c.createStatement()) {
        s.setQueryTimeout(5);
        s.execute("SET LOCAL lock_timeout='2s'");
        s.execute(sql);
        tx.executeWithoutResult(
            status -> {
              metadata.run();
              try {
                c.commit();
              } catch (SQLException e) {
                throw new ApiException(503, "The database change could not be committed.");
              }
            });
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new ApiException(
          409,
          "The change could not be applied. Required columns can only be added to empty tables."
              + " Check dependencies and active queries, then retry.");
    }
  }
}
