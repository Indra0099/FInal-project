package com.shardquery.workspace;

import static com.shardquery.workspace.WorkspaceTypes.*;

import com.shardquery.auth.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/databases")
public class WorkspaceController {
  private final WorkspaceService service;

  private final DatabaseManagementService management;
  private final ExternalDatabaseService external;

  public WorkspaceController(
      WorkspaceService service,
      DatabaseManagementService management,
      ExternalDatabaseService external) {
    this.service = service;
    this.management = management;
    this.external = external;
  }

  @PostMapping("/connect")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object connect(
      @AuthenticationPrincipal AccountPrincipal u, @Valid @RequestBody ConnectDatabase request) {
    return service.databaseView(
        service.ownedDatabase(u.id(), external.save(u.id(), null, request)));
  }

  @PutMapping("/{database}/connection")
  public Object updateConnection(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @Valid @RequestBody ConnectDatabase request) {
    external.save(u.id(), database, request);
    return service.databaseView(service.ownedDatabase(u.id(), database));
  }

  @PostMapping("/{database}/refresh")
  public Object refresh(@AuthenticationPrincipal AccountPrincipal u, @PathVariable UUID database) {
    external.refresh(u.id(), database);
    return service.databaseView(service.ownedDatabase(u.id(), database));
  }

  @PatchMapping("/{database}")
  public Object renameDatabase(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @Valid @RequestBody Rename request) {
    management.renameDatabase(u.id(), database, request);
    return service.databaseView(service.ownedDatabase(u.id(), database));
  }

  @DeleteMapping("/{database}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteDatabase(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @Valid @RequestBody Confirmation request) {
    management.deleteDatabase(u.id(), database, request);
  }

  @PatchMapping("/{database}/tables/{table}")
  public Object renameTable(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @Valid @RequestBody Rename request) {
    management.renameTable(u.id(), database, table, request);
    return service.tables(u.id(), database);
  }

  @DeleteMapping("/{database}/tables/{table}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteTable(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @Valid @RequestBody Confirmation request) {
    management.deleteTable(u.id(), database, table, request);
  }

  @PostMapping("/{database}/tables/{table}/columns")
  public Object addColumn(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @Valid @RequestBody Column request)
      throws Exception {
    management.addColumn(u.id(), database, table, request);
    return service.tables(u.id(), database);
  }

  @PatchMapping("/{database}/tables/{table}/columns/{column}")
  public Object renameColumn(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @PathVariable String column,
      @Valid @RequestBody RenameColumn request)
      throws Exception {
    management.renameColumn(u.id(), database, table, column, request);
    return service.tables(u.id(), database);
  }

  @DeleteMapping("/{database}/tables/{table}/columns/{column}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteColumn(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @PathVariable String column,
      @Valid @RequestBody Confirmation request)
      throws Exception {
    management.deleteColumn(u.id(), database, table, column, request);
  }

  @DeleteMapping("/{database}/tables/{table}/rows/{row}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteRow(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @PathVariable long row,
      @Valid @RequestBody Confirmation request) {
    management.deleteRow(u.id(), database, table, row, request);
  }

  @GetMapping
  public Object databases(@AuthenticationPrincipal AccountPrincipal u) {
    return service.list(u.id());
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(
      @AuthenticationPrincipal AccountPrincipal u, @Valid @RequestBody CreateDatabase request) {
    return service.createDatabase(u.id(), request);
  }

  @GetMapping("/tables")
  public Object allTables(@AuthenticationPrincipal AccountPrincipal u) {
    return service.allTables(u.id());
  }

  @PostMapping("/{database}/retry")
  public Object retry(@AuthenticationPrincipal AccountPrincipal u, @PathVariable UUID database) {
    return service.retryDatabase(u.id(), database);
  }

  @GetMapping("/{database}/tables")
  public Object tables(@AuthenticationPrincipal AccountPrincipal u, @PathVariable UUID database) {
    return service.tables(u.id(), database);
  }

  @PostMapping("/{database}/tables")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object createTable(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @Valid @RequestBody CreateTable request)
      throws Exception {
    return service.createTable(u.id(), database, request);
  }

  @GetMapping("/{database}/tables/{table}/rows")
  public Object rows(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @RequestParam(defaultValue = "0") int page)
      throws Exception {
    return service.rows(u.id(), database, table, page);
  }

  @PostMapping("/{database}/tables/{table}/rows")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object insert(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @Valid @RequestBody Row request)
      throws Exception {
    return service.saveRow(u.id(), database, table, null, request);
  }

  @PutMapping("/{database}/tables/{table}/rows/{row}")
  public Object update(
      @AuthenticationPrincipal AccountPrincipal u,
      @PathVariable UUID database,
      @PathVariable UUID table,
      @PathVariable long row,
      @Valid @RequestBody Row request)
      throws Exception {
    return service.saveRow(u.id(), database, table, row, request);
  }
}
