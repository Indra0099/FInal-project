package com.shardquery;

import static com.shardquery.query.QueryTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardquery.ai.GeminiGateway;
import com.shardquery.document.DocumentService;
import com.shardquery.infrastructure.ShardManager;
import com.shardquery.persistence.*;
import com.shardquery.query.QueryService;
import com.shardquery.web.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.ai-key=contract-test-only",
      "app.ai-url=http://127.0.0.1:4017/openai/v1",
      "app.upload-dir=../.local/test-uploads",
      "app.collection=shardquery_contract_test",
      "app.write-limit=500"
    })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SQ_INTEGRATION", matches = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InfrastructureIntegrationTest {
  @Autowired QueryService queries;
  @Autowired ShardManager shards;
  @Autowired DocumentService documents;
  @Autowired DocumentRepository docs;
  @Autowired QueryJobRepository jobs;
  @Autowired ObjectMapper mapper;
  @Autowired GeminiGateway ai;
  @Autowired TestRestTemplate http;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired com.shardquery.auth.AuthService auth;
  @Autowired com.shardquery.workspace.WorkspaceService workspaces;
  @Autowired UserDatabaseRepository databases;
  @Autowired UserTableRepository tables;
  @Autowired com.shardquery.workspace.DatabaseManagementService management;
  @Autowired com.shardquery.workspace.ExternalDatabaseService external;
  UUID owner, otherOwner;

  @BeforeAll
  void accounts() {
    owner =
        auth.register(
                "Integration test",
                "test-" + UUID.randomUUID() + "@example.test",
                "Integration-only-passphrase")
            .id();
    otherOwner =
        auth.register(
                "Other test",
                "test-" + UUID.randomUUID() + "@example.test",
                "Integration-only-passphrase")
            .id();
  }

  org.springframework.test.web.servlet.request.RequestPostProcessor as(UUID id) {
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken
            .authenticated(
                new com.shardquery.auth.AccountPrincipal(id, "Test", "test@example.test"),
                null,
                List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_USER"))));
  }

  List<UUID> docIds = new ArrayList<>(), jobIds = new ArrayList<>();

  QueryJob completed(Request request) throws Exception {
    var submitted = queries.submit(request, owner);
    jobIds.add(submitted.id);
    for (int i = 0; i < 200; i++) {
      var job = queries.get(submitted.id, owner);
      if (!List.of("QUEUED", "RUNNING").contains(job.status)) return job;
      Thread.sleep(150);
    }
    fail("Query did not finish");
    return null;
  }

  @Test
  void realPostgresAssociationAndHashRouting() throws Exception {
    var plan =
        new Plan(
            Mode.SQL,
            Intent.PURCHASES,
            "laptop",
            new BigDecimal("50000"),
            null,
            null,
            50,
            "Integration test");
    var job =
        completed(new Request("contract-test purchases " + UUID.randomUUID(), Mode.SQL, plan));
    assertEquals("COMPLETE", job.status, job.error);
    var result = mapper.readTree(job.resultJson);
    assertEquals(4, result.path("rows").size());
    assertEquals(3, result.path("traces").size());
    assertEquals("Asha Rao", result.path("rows").path(0).path("customer_name").asText());
    var routed =
        new Plan(
            Mode.SQL,
            Intent.PURCHASES,
            "laptop",
            new BigDecimal("50000"),
            101,
            null,
            50,
            "Integration test");
    var single =
        mapper.readTree(
            completed(new Request("contract-test customer " + UUID.randomUUID(), Mode.SQL, routed))
                .resultJson);
    assertEquals(1, single.path("rows").size());
    assertEquals(2, single.path("traces").path(1).path("bucket").asInt());
  }

  @Test
  void readOnlyRoleActuallyRejectsWrites() {
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () ->
            shards
                .jdbc("customer")
                .getJdbcTemplate()
                .update("UPDATE customers SET name='forbidden' WHERE customer_id=-1"));
  }

  @Test
  void httpValidationAndNotFound() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/queries")
                .with(as(owner))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json")
                .content("{\"question\":\"x\",\"mode\":\"SQL\"}"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isBadRequest());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/queries/" + UUID.randomUUID())
                .with(as(owner)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isNotFound());
  }

  @Test
  void providerRateLimitIsHandled() {
    var error = assertThrows(ApiException.class, () -> ai.plan("SIMULATE_RATE_LIMIT", Mode.AUTO));
    assertEquals(503, error.status);
  }

  @Test
  void rejectsUnknownCitations() {
    var source =
        new Source("D1", UUID.randomUUID(), "contract.txt", 1, "Actual excerpt", 0.8, 201L, 101L);
    var error =
        assertThrows(
            ApiException.class,
            () -> ai.answer("SIMULATE_BAD_CITATION", List.of(), List.of(source), List.of()));
    assertEquals(502, error.status);
  }

  @Test
  void correctsCitationFormattingUsingTheSameEvidence() {
    var source =
        new Source("D1", UUID.randomUUID(), "contract.txt", 1, "Actual excerpt", 0.8, 201L, 101L);
    String answer =
        ai.answer(
            "SIMULATE_CITATION_CORRECTION " + UUID.randomUUID(),
            List.of(),
            List.of(source),
            List.of());
    assertTrue(answer.contains("[D1]"));
  }

  @Test
  void kafkaIndexesIdempotentlyAndHybridFiltersCustomerProductPairs() throws Exception {
    String unique = UUID.randomUUID().toString();
    var goodFile =
        new MockMultipartFile(
            "file",
            "contract-test-matching.txt",
            "text/plain",
            ("Matching laptop review " + unique).getBytes(StandardCharsets.UTF_8));
    var good = documents.upload(goodFile, 201L, 101L, owner);
    docIds.add(good.id);
    var foreign = documents.upload(goodFile, 201L, 101L, otherOwner);
    docIds.add(foreign.id);
    assertNotEquals(good.id, foreign.id);
    var duplicate = documents.upload(goodFile, 201L, 101L, owner);
    assertEquals(good.id, duplicate.id);
    var other =
        documents.upload(
            new MockMultipartFile(
                "file",
                "contract-test-unrelated.txt",
                "text/plain",
                ("Unrelated customer laptop review " + unique).getBytes()),
            201L,
            999L,
            owner);
    docIds.add(other.id);
    for (int i = 0; i < 300; i++) {
      if (List.of(good.id, other.id, foreign.id).stream()
          .allMatch(id -> docs.findById(id).orElseThrow().status.equals("READY"))) break;
      Thread.sleep(200);
    }
    assertEquals(
        "READY", documents.get(good.id, owner).status, documents.get(good.id, owner).error);
    assertEquals(
        "READY", documents.get(other.id, owner).status, documents.get(other.id, owner).error);
    int attempts = documents.get(good.id, owner).attempts;
    documents.consume(good.id.toString());
    assertEquals(attempts, documents.get(good.id, owner).attempts);
    var job =
        completed(
            new Request("contract-test laptop purchases and reviews " + unique, Mode.HYBRID, null));
    assertEquals("COMPLETE", job.status, job.error);
    var result = mapper.readTree(job.resultJson);
    assertEquals(4, result.path("rows").size());
    assertEquals(1, result.path("sources").size());
    assertEquals(good.id.toString(), result.path("sources").path(0).path("documentId").asText());
    assertTrue(result.path("answer").asText().contains("[D1]"));
  }

  @Test
  void sessionRegistrationLoginCsrfAndLogoutOverHttp() throws Exception {
    var cookies = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
    var client = java.net.http.HttpClient.newBuilder().cookieHandler(cookies).build();
    String email = "http-test-" + UUID.randomUUID() + "@example.test";
    var body =
        Map.of(
            "name",
            "HTTP Test",
            "email",
            email.toUpperCase(Locale.ROOT),
            "password",
            "Test-only-12-character-passphrase");
    assertEquals(401, call(client, "GET", "/api/databases", null, null).statusCode());
    assertEquals(403, call(client, "POST", "/api/auth/register", body, null).statusCode());
    String csrf = csrf(client);
    var weak = new HashMap<String, Object>(body);
    weak.put("password", "short");
    assertEquals(400, call(client, "POST", "/api/auth/register", weak, csrf).statusCode());
    var created = call(client, "POST", "/api/auth/register", body, csrf);
    assertEquals(201, created.statusCode(), created.body());
    assertEquals(email, mapper.readTree(created.body()).path("email").asText());
    assertFalse(created.body().contains("password"));
    UUID createdId = UUID.fromString(mapper.readTree(created.body()).path("id").asText());
    try {
      var user = users.findById(createdId).orElseThrow();
      assertTrue(user.passwordHash.startsWith("$2"));
      assertNotEquals(body.get("password"), user.passwordHash);
      assertEquals(200, call(client, "GET", "/api/auth/me", null, null).statusCode());
      assertEquals("[]", call(client, "GET", "/api/queries", null, null).body());
      assertEquals("[]", call(client, "GET", "/api/documents", null, null).body());
      assertEquals(
          403,
          call(client, "POST", "/api/databases", Map.of("name", "Rejected"), csrf)
              .statusCode()); // pre-login token is invalid
      csrf = csrf(client);
      assertEquals(409, call(client, "POST", "/api/auth/register", body, csrf).statusCode());
      assertEquals(204, call(client, "POST", "/api/auth/logout", null, csrf).statusCode());
      assertEquals(401, call(client, "GET", "/api/auth/me", null, null).statusCode());
      csrf = csrf(client);
      assertEquals(
          401,
          call(
                  client,
                  "POST",
                  "/api/auth/login",
                  Map.of("email", email, "password", "wrong-password"),
                  csrf)
              .statusCode());
      String prior =
          cookies.getCookieStore().getCookies().stream()
              .filter(c -> c.getName().equals("SHARDQUERY_SESSION"))
              .findFirst()
              .orElseThrow()
              .getValue();
      assertEquals(
          200,
          call(
                  client,
                  "POST",
                  "/api/auth/login",
                  Map.of("email", email, "password", body.get("password")),
                  csrf)
              .statusCode());
      String next =
          cookies.getCookieStore().getCookies().stream()
              .filter(c -> c.getName().equals("SHARDQUERY_SESSION"))
              .findFirst()
              .orElseThrow()
              .getValue();
      assertNotEquals(prior, next, "Login must rotate the session ID");
      assertEquals(204, call(client, "POST", "/api/auth/logout", null, csrf(client)).statusCode());
    } finally {
      users.deleteById(createdId);
    }
  }

  java.net.http.HttpResponse<String> call(
      java.net.http.HttpClient client, String method, String path, Object body, String csrf)
      throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create(http.getRootUri() + path))
            .timeout(java.time.Duration.ofSeconds(25));
    if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
    request.header("Content-Type", "application/json");
    request.method(
        method,
        body == null
            ? java.net.http.HttpRequest.BodyPublishers.noBody()
            : java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
    return client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
  }

  String csrf(java.net.http.HttpClient client) throws Exception {
    return mapper
        .readTree(call(client, "GET", "/api/auth/csrf", null, null).body())
        .path("token")
        .asText();
  }

  @Test
  void privateResourcesRejectAnotherAccount() throws Exception {
    var doc = new DocumentRecord();
    doc.id = UUID.randomUUID();
    doc.userId = owner;
    doc.name = "private-test.txt";
    doc.fingerprint = doc.id.toString();
    doc.status = "FAILED";
    doc.createdAt = doc.updatedAt = doc.nextDispatch = java.time.Instant.now();
    docs.saveAndFlush(doc);
    docIds.add(doc.id);
    assertEquals(
        404, assertThrows(ApiException.class, () -> documents.get(doc.id, otherOwner)).status);
    assertEquals(
        404, assertThrows(ApiException.class, () -> documents.retry(doc.id, otherOwner)).status);
    assertEquals(
        404, assertThrows(ApiException.class, () -> documents.bytes(doc.id, otherOwner)).status);
    assertTrue(documents.list(otherOwner).stream().noneMatch(d -> d.id.equals(doc.id)));
    assertTrue(documents.list(otherOwner).stream().allMatch(d -> otherOwner.equals(d.userId)));
    var job =
        completed(
            new Request(
                "Owned sample catalogue " + UUID.randomUUID(),
                Mode.SQL,
                new Plan(Mode.SQL, Intent.PRODUCTS, null, null, null, null, 20, "Fixture")));
    assertEquals(
        404, assertThrows(ApiException.class, () -> queries.get(job.id, otherOwner)).status);
    assertTrue(queries.history(otherOwner).isEmpty());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/documents/" + doc.id + "/download")
                .with(as(otherOwner)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isNotFound());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/queries/" + job.id)
                .with(as(otherOwner)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isNotFound());
  }

  @Test
  void createsRealDatabaseTablesAndRowsWithOwnerIsolationAndBoundSql() throws Exception {
    var created =
        workspaces.createDatabase(
            owner,
            new com.shardquery.workspace.WorkspaceTypes.CreateDatabase(
                "Integration " + UUID.randomUUID()));
    UUID id = (UUID) created.get("id");
    var database = databases.findById(id).orElseThrow();
    try {
      assertEquals("READY", created.get("status"));
      assertFalse(created.containsKey("physicalName"));
      assertTrue(workspaces.list(otherOwner).isEmpty());
      assertEquals(
          404, assertThrows(ApiException.class, () -> workspaces.tables(otherOwner, id)).status);
      var fields =
          List.of(
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "student_name", com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT, true),
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "marks", com.shardquery.workspace.WorkspaceTypes.FieldType.INTEGER, true),
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "joined", com.shardquery.workspace.WorkspaceTypes.FieldType.DATE, false));
      var table =
          workspaces.createTable(
              owner,
              id,
              new com.shardquery.workspace.WorkspaceTypes.CreateTable("Students", fields));
      UUID tid = (UUID) table.get("id");
      String malicious = "O'Brien'; DROP TABLE students; --";
      var row =
          workspaces.saveRow(
              owner,
              id,
              tid,
              null,
              new com.shardquery.workspace.WorkspaceTypes.Row(
                  Map.of("student_name", malicious, "marks", "92", "joined", "2026-11-08")));
      assertEquals(malicious, row.get("student_name"));
      assertEquals("92", row.get("marks"));
      assertEquals("2026-11-08", row.get("joined"));
      assertEquals(1L, workspaces.rows(owner, id, tid, 0).get("total"));
      assertEquals(
          404,
          assertThrows(
                  ApiException.class,
                  () ->
                      workspaces.saveRow(
                          otherOwner,
                          id,
                          tid,
                          null,
                          new com.shardquery.workspace.WorkspaceTypes.Row(
                              Map.of("student_name", "Intruder", "marks", 10))))
              .status);
      assertEquals(
          400,
          assertThrows(
                  ApiException.class,
                  () ->
                      workspaces.saveRow(
                          owner,
                          id,
                          tid,
                          null,
                          new com.shardquery.workspace.WorkspaceTypes.Row(
                              Map.of("student_name", "Invalid", "marks", "not a number"))))
              .status);
      assertEquals(
          400,
          assertThrows(
                  ApiException.class,
                  () ->
                      workspaces.saveRow(
                          owner,
                          id,
                          tid,
                          null,
                          new com.shardquery.workspace.WorkspaceTypes.Row(Map.of("marks", 12))))
              .status);
      workspaces.saveRow(
          owner,
          id,
          tid,
          ((Number) row.get("_row_id")).longValue(),
          new com.shardquery.workspace.WorkspaceTypes.Row(
              Map.of("student_name", "Updated", "marks", 95)));
      var answer =
          completed(
              new Request(
                  "contract-test personal students above 80 " + UUID.randomUUID(),
                  Mode.SQL,
                  null,
                  tid));
      assertEquals("COMPLETE", answer.status, answer.error);
      assertEquals(1, mapper.readTree(answer.resultJson).path("rows").size());
      assertEquals(tid.toString(), mapper.readTree(answer.resultJson).path("tableId").asText());
      assertEquals(50, mapper.readTree(answer.resultJson).path("plan").path("limit").asInt());
      assertEquals(
          "Updated",
          mapper.readTree(answer.resultJson).path("rows").path(0).path("student_name").asText());
      var unsafe =
          mapper.readTree(
              "{\"filters\":[{\"column\":\"marks; DROP TABLE"
                  + " x\",\"operator\":\"EQ\",\"value\":\"1\"}],\"limit\":20,\"orderBy\":null,\"descending\":false,\"unsupported\":false,\"explanation\":\"test\"}");
      assertEquals(
          422,
          assertThrows(
                  ApiException.class,
                  () ->
                      workspaces.executePlan(database, tables.findById(tid).orElseThrow(), unsafe))
              .status);
      assertEquals(
          404,
          assertThrows(ApiException.class, () -> workspaces.query(otherOwner, tid, "read data"))
              .status);
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      "/api/databases/" + id + "/tables/" + tid + "/rows")
                  .with(as(otherOwner)))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isNotFound());
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/databases/" + id + "/tables")
                  .with(as(owner))
                  .with(
                      org.springframework.security.test.web.servlet.request
                          .SecurityMockMvcRequestPostProcessors.csrf())
                  .contentType("application/json")
                  .content(
                      "{\"name\":\"Unsafe\",\"columns\":[{\"name\":\"x); drop table"
                          + " y\",\"type\":\"TEXT\",\"required\":true}]}"))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isBadRequest());
    } finally {
      try (var c =
              java.sql.DriverManager.getConnection(
                  "jdbc:postgresql://localhost:5643/shard_product_db",
                  "shard_workspace",
                  System.getenv()
                      .getOrDefault("WORKSPACE_PASSWORD", "local-workspaces-change-me"));
          var s = c.createStatement()) {
        s.execute(
            "DROP DATABASE "
                + com.shardquery.workspace.WorkspaceService.identifier(database.physicalName));
      }
      tables.deleteAll(tables.findByDatabaseIdOrderByCreatedAtAsc(id));
      databases.deleteById(id);
    }
  }

  @Test
  void automaticallyRoutesStudentFeesQuestionAcrossTwoOwnedDatabases() throws Exception {
    var databaseIds = new ArrayList<UUID>();
    try {
      UUID studentDb =
          (UUID)
              workspaces
                  .createDatabase(
                      owner, new com.shardquery.workspace.WorkspaceTypes.CreateDatabase("Students"))
                  .get("id");
      databaseIds.add(studentDb);
      UUID feesDb =
          (UUID)
              workspaces
                  .createDatabase(
                      owner,
                      new com.shardquery.workspace.WorkspaceTypes.CreateDatabase("Students fees"))
                  .get("id");
      databaseIds.add(feesDb);
      var studentColumns =
          List.of(
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "id", com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT, true),
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "name", com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT, true),
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "group", com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT, true));
      var feeColumns =
          List.of(
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "id", com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT, true),
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "student_id", com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT, true),
              new com.shardquery.workspace.WorkspaceTypes.Column(
                  "fees", com.shardquery.workspace.WorkspaceTypes.FieldType.INTEGER, true));
      UUID studentId =
          (UUID)
              workspaces
                  .createTable(
                      owner,
                      studentDb,
                      new com.shardquery.workspace.WorkspaceTypes.CreateTable(
                          "student", studentColumns))
                  .get("id");
      UUID feeId =
          (UUID)
              workspaces
                  .createTable(
                      owner,
                      feesDb,
                      new com.shardquery.workspace.WorkspaceTypes.CreateTable(
                          "student fees", feeColumns))
                  .get("id");
      workspaces.saveRow(
          owner,
          studentDb,
          studentId,
          null,
          new com.shardquery.workspace.WorkspaceTypes.Row(
              Map.of("id", "S1", "name", "Asha", "group", "CSE")));
      workspaces.saveRow(
          owner,
          studentDb,
          studentId,
          null,
          new com.shardquery.workspace.WorkspaceTypes.Row(
              Map.of("id", "S2", "name", "Ravi", "group", "ECE")));
      workspaces.saveRow(
          owner,
          feesDb,
          feeId,
          null,
          new com.shardquery.workspace.WorkspaceTypes.Row(
              Map.of("id", "F10", "student_id", "S1", "fees", "220000")));
      workspaces.saveRow(
          owner,
          feesDb,
          feeId,
          null,
          new com.shardquery.workspace.WorkspaceTypes.Row(
              Map.of("id", "F11", "student_id", "S2", "fees", "150000")));
      var job =
          completed(
              new Request(
                  "find the students in database whose fees is above ₹150000", Mode.AUTO, null));
      assertEquals("COMPLETE", job.status, job.error);
      var result = mapper.readTree(job.resultJson);
      assertEquals("PERSONAL", result.path("dataSource").asText());
      assertEquals(2, result.path("tables").size());
      assertEquals(2, result.path("traces").size());
      assertEquals(1, result.path("rows").size());
      assertEquals("Asha", result.path("rows").get(0).path("Students / student.name").asText());
      assertEquals(
          "220000", result.path("rows").get(0).path("Students fees / student fees.fees").asText());
      assertEquals("AUTO", result.path("requestSource").asText());
      assertFalse(result.path("cached").asBoolean());
      var personalOnly =
          completed(
              new Request(
                  "find the students whose fees is above 150000",
                  Mode.SQL,
                  null,
                  null,
                  DataSource.PERSONAL));
      assertEquals("COMPLETE", personalOnly.status, personalOnly.error);
      var invented = completed(new Request("SIMULATE_FOREIGN_TABLE", Mode.AUTO, null));
      assertEquals("FAILED", invented.status);
      assertTrue(invented.error.contains("not available in your account"));
      assertTrue(workspaces.catalog(otherOwner).isEmpty());
    } finally {
      for (var id : databaseIds) {
        var d = databases.findById(id).orElseThrow();
        try (var c =
                java.sql.DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5643/shard_product_db",
                    "shard_workspace",
                    System.getenv()
                        .getOrDefault("WORKSPACE_PASSWORD", "local-workspaces-change-me"));
            var st = c.createStatement()) {
          st.execute(
              "DROP DATABASE "
                  + com.shardquery.workspace.WorkspaceService.identifier(d.physicalName));
        }
        tables.deleteAll(tables.findByDatabaseIdOrderByCreatedAtAsc(id));
        databases.deleteById(id);
      }
    }
  }

  @Test
  void databaseLifecyclePreservesRowsAndRequiresOwnershipAndConfirmation() throws Exception {
    var db =
        workspaces.createDatabase(
            owner, new com.shardquery.workspace.WorkspaceTypes.CreateDatabase("Lifecycle fixture"));
    UUID id = UUID.fromString(db.get("id").toString());
    try {
      var created =
          workspaces.createTable(
              owner,
              id,
              new com.shardquery.workspace.WorkspaceTypes.CreateTable(
                  "students",
                  List.of(
                      new com.shardquery.workspace.WorkspaceTypes.Column(
                          "name", com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT, true))));
      UUID table = UUID.fromString(created.get("id").toString());
      var row =
          workspaces.saveRow(
              owner,
              id,
              table,
              null,
              new com.shardquery.workspace.WorkspaceTypes.Row(Map.of("name", "Preserved student")));
      long rowId = ((Number) row.get("_row_id")).longValue();
      assertThrows(
          ApiException.class,
          () ->
              management.deleteDatabase(
                  otherOwner,
                  id,
                  new com.shardquery.workspace.WorkspaceTypes.Confirmation("Lifecycle fixture")));
      assertThrows(
          ApiException.class,
          () ->
              management.deleteDatabase(
                  owner, id, new com.shardquery.workspace.WorkspaceTypes.Confirmation("wrong")));
      management.renameDatabase(
          owner, id, new com.shardquery.workspace.WorkspaceTypes.Rename("Renamed fixture"));
      management.renameTable(
          owner, id, table, new com.shardquery.workspace.WorkspaceTypes.Rename("learners"));
      assertThrows(
          ApiException.class,
          () ->
              management.addColumn(
                  owner,
                  id,
                  table,
                  new com.shardquery.workspace.WorkspaceTypes.Column(
                      "required_new",
                      com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT,
                      true)));
      management.addColumn(
          owner,
          id,
          table,
          new com.shardquery.workspace.WorkspaceTypes.Column(
              "score", com.shardquery.workspace.WorkspaceTypes.FieldType.INTEGER, false));
      management.renameColumn(
          owner,
          id,
          table,
          "name",
          new com.shardquery.workspace.WorkspaceTypes.RenameColumn("student_name"));
      var records = mapper.valueToTree(workspaces.rows(owner, id, table, 0));
      assertEquals("Preserved student", records.path("rows").path(0).path("student_name").asText());
      assertTrue(records.path("rows").path(0).path("score").isNull());
      workspaces.saveRow(
          owner,
          id,
          table,
          rowId,
          new com.shardquery.workspace.WorkspaceTypes.Row(
              Map.of("student_name", "Preserved student", "score", 91)));
      management.deleteColumn(
          owner,
          id,
          table,
          "score",
          new com.shardquery.workspace.WorkspaceTypes.Confirmation("score"));
      assertEquals(1, workspaces.columns(tables.findById(table).orElseThrow()).size());
      assertThrows(
          ApiException.class,
          () ->
              management.deleteColumn(
                  owner,
                  id,
                  table,
                  "student_name",
                  new com.shardquery.workspace.WorkspaceTypes.Confirmation("student_name")));
      management.deleteRow(
          owner,
          id,
          table,
          rowId,
          new com.shardquery.workspace.WorkspaceTypes.Confirmation(Long.toString(rowId)));
      assertEquals(
          0, mapper.valueToTree(workspaces.rows(owner, id, table, 0)).path("total").asInt());
      management.deleteTable(
          owner, id, table, new com.shardquery.workspace.WorkspaceTypes.Confirmation("learners"));
      assertTrue(workspaces.tables(owner, id).isEmpty());
    } finally {
      management.deleteDatabase(
          owner,
          id,
          new com.shardquery.workspace.WorkspaceTypes.Confirmation(
              databases.findById(id).orElseThrow().name));
    }
    assertFalse(databases.existsById(id));
  }

  @Test
  void externalConnectionQueriesLiveRowsRefreshesAndDisconnectsWithoutDroppingSource()
      throws Exception {
    var db =
        workspaces.createDatabase(
            owner,
            new com.shardquery.workspace.WorkspaceTypes.CreateDatabase("External fixture source"));
    UUID localId = UUID.fromString(db.get("id").toString());
    UUID connected = null;
    var source = databases.findById(localId).orElseThrow();
    String password =
        System.getenv().getOrDefault("WORKSPACE_PASSWORD", "local-workspaces-change-me");
    try {
      var created =
          workspaces.createTable(
              owner,
              localId,
              new com.shardquery.workspace.WorkspaceTypes.CreateTable(
                  "grades",
                  List.of(
                      new com.shardquery.workspace.WorkspaceTypes.Column(
                          "name", com.shardquery.workspace.WorkspaceTypes.FieldType.TEXT, true),
                      new com.shardquery.workspace.WorkspaceTypes.Column(
                          "marks",
                          com.shardquery.workspace.WorkspaceTypes.FieldType.INTEGER,
                          true))));
      UUID localTable = UUID.fromString(created.get("id").toString());
      String physical = tables.findById(localTable).orElseThrow().physicalName;
      try (var c =
              java.sql.DriverManager.getConnection(
                  "jdbc:postgresql://localhost:5643/" + source.physicalName,
                  "shard_workspace",
                  password);
          var stmt = c.createStatement()) {
        stmt.execute(
            "ALTER TABLE "
                + com.shardquery.workspace.WorkspaceService.identifier(physical)
                + " RENAME TO grades");
        stmt.execute(
            "ALTER TABLE grades ADD COLUMN reference_id uuid DEFAULT"
                + " 'b0bd58e9-fd26-4b4e-9be2-d9af726d094b'");
        stmt.execute(
            "INSERT INTO grades(name,marks) SELECT 'Fictional student ' || i,i FROM"
                + " generate_series(1,1005) i");
      }
      // Remove local catalog entry only, leaving the actual source table for external discovery.
      tables.deleteById(localTable);
      String url =
          "postgresql://shard_workspace:"
              + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8)
              + "@localhost:5643/"
              + source.physicalName
              + "?sslmode=disable";
      connected =
          external.save(
              owner,
              null,
              new com.shardquery.workspace.WorkspaceTypes.ConnectDatabase(
                  "Connected grades", url, "public"));
      UUID connectionId = connected;
      var d = databases.findById(connected).orElseThrow();
      assertFalse(d.connectionSecret.contains(password));
      String view = mapper.writeValueAsString(workspaces.list(owner));
      assertFalse(view.contains(password));
      assertFalse(view.contains("postgresql://"));
      assertFalse(view.contains("connectionSecret"));
      var remote = tables.findByDatabaseIdOrderByCreatedAtAsc(connected).getFirst();
      var uuidPlan =
          mapper.readTree(
              "{\"unsupported\":false,\"filters\":[{\"column\":\"reference_id\",\"operator\":\"EQ\",\"value\":\"b0bd58e9-fd26-4b4e-9be2-d9af726d094b\"}],\"orderBy\":null,\"descending\":false,\"limit\":1,\"explanation\":\"UUID"
                  + " comparison test\"}");
      assertEquals(1, workspaces.executePlan(d, remote, uuidPlan).data().rows().size());
      assertEquals("grades", remote.name);
      assertTrue(
          mapper
              .valueToTree(workspaces.rows(owner, connected, remote.id, 0))
              .path("truncated")
              .asBoolean());
      assertThrows(
          ApiException.class,
          () ->
              workspaces.saveRow(
                  owner,
                  connectionId,
                  remote.id,
                  null,
                  new com.shardquery.workspace.WorkspaceTypes.Row(
                      Map.of("name", "forbidden", "marks", 1))));
      assertThrows(
          ApiException.class,
          () ->
              management.deleteTable(
                  owner,
                  connectionId,
                  remote.id,
                  new com.shardquery.workspace.WorkspaceTypes.Confirmation("grades")));
      assertThrows(ApiException.class, () -> external.refresh(otherOwner, connectionId));
      try (var c = external.connect(d);
          var stmt = c.createStatement()) {
        assertTrue(c.isReadOnly());
        assertThrows(java.sql.SQLException.class, () -> stmt.execute("DELETE FROM public.grades"));
        c.rollback();
      }
      var job =
          completed(
              new Request(
                  "Find connected students with marks above 80",
                  Mode.AUTO,
                  null,
                  null,
                  DataSource.PERSONAL));
      assertEquals("COMPLETE", job.status, job.error);
      var result = mapper.readTree(job.resultJson);
      assertEquals(50, result.path("rows").size());
      assertEquals(1005, result.path("rows").path(0).path("marks").asInt());
      assertTrue(result.path("traces").path(0).path("sql").asText().contains("WHERE"));
      String oldSecret = d.connectionSecret;
      assertThrows(
          ApiException.class,
          () ->
              external.save(
                  owner,
                  connectionId,
                  new com.shardquery.workspace.WorkspaceTypes.ConnectDatabase(
                      "Connected grades",
                      "postgresql://u:p@not-enabled.invalid/school",
                      "public")));
      assertEquals(oldSecret, databases.findById(connected).orElseThrow().connectionSecret);
      try (var c =
              java.sql.DriverManager.getConnection(
                  "jdbc:postgresql://localhost:5643/" + source.physicalName,
                  "shard_workspace",
                  password);
          var stmt = c.createStatement()) {
        stmt.execute("ALTER TABLE grades ADD COLUMN department text");
      }
      external.refresh(owner, connected);
      assertEquals(remote.id, tables.findByDatabaseIdOrderByCreatedAtAsc(connected).getFirst().id);
      assertEquals(5, workspaces.columns(tables.findById(remote.id).orElseThrow()).size());
      management.deleteDatabase(
          owner,
          connected,
          new com.shardquery.workspace.WorkspaceTypes.Confirmation("Connected grades"));
      connected = null;
      try (var c =
              java.sql.DriverManager.getConnection(
                  "jdbc:postgresql://localhost:5643/" + source.physicalName,
                  "shard_workspace",
                  password);
          var stmt = c.createStatement();
          var r = stmt.executeQuery("SELECT count(*) FROM grades")) {
        r.next();
        assertEquals(1005, r.getInt(1));
      }
    } finally {
      if (connected != null)
        management.deleteDatabase(
            owner,
            connected,
            new com.shardquery.workspace.WorkspaceTypes.Confirmation("Connected grades"));
      management.deleteDatabase(
          owner,
          localId,
          new com.shardquery.workspace.WorkspaceTypes.Confirmation("External fixture source"));
    }
  }

  @Test
  void managementHttpRejectsMissingCsrfAndOtherOwners() throws Exception {
    var db =
        workspaces.createDatabase(
            owner,
            new com.shardquery.workspace.WorkspaceTypes.CreateDatabase("HTTP lifecycle fixture"));
    UUID id = UUID.fromString(db.get("id").toString());
    try {
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                      "/api/databases/" + id)
                  .with(as(owner))
                  .contentType("application/json")
                  .content("{\"name\":\"Renamed fixture\"}"))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isForbidden());
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      "/api/databases/" + id)
                  .with(as(otherOwner))
                  .with(
                      org.springframework.security.test.web.servlet.request
                          .SecurityMockMvcRequestPostProcessors.csrf())
                  .contentType("application/json")
                  .content("{\"confirmation\":\"HTTP lifecycle fixture\"}"))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isNotFound());
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      "/api/databases/" + id)
                  .with(as(owner))
                  .with(
                      org.springframework.security.test.web.servlet.request
                          .SecurityMockMvcRequestPostProcessors.csrf())
                  .contentType("application/json")
                  .content("{\"confirmation\":\"wrong\"}"))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isConflict());
    } finally {
      management.deleteDatabase(
          owner,
          id,
          new com.shardquery.workspace.WorkspaceTypes.Confirmation("HTTP lifecycle fixture"));
    }
  }

  @AfterAll
  void cleanup() throws Exception {
    jobs.deleteAllById(jobIds);
    docs.deleteAllById(docIds);
    users.deleteById(owner);
    users.deleteById(otherOwner);
    for (var id : docIds) Files.deleteIfExists(Path.of("../.local/test-uploads", id + ".bin"));
  }
}
