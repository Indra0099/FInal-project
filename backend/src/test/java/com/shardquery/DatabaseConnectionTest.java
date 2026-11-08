package com.shardquery;

import static org.junit.jupiter.api.Assertions.*;

import com.shardquery.web.ApiException;
import com.shardquery.workspace.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseConnectionTest {
  @TempDir Path directory;

  @Test
  void credentialsAreEncryptedAndBoundToOneConnection() throws Exception {
    var secrets = new ConnectionSecrets(directory.toString());
    UUID id = UUID.randomUUID();
    String link = "postgresql://reader:secret@localhost/school";
    String ciphertext = secrets.encrypt(id, link);
    assertFalse(ciphertext.contains("secret"));
    assertNotEquals(ciphertext, secrets.encrypt(id, link));
    assertEquals(link, new ConnectionSecrets(directory.toString()).decrypt(id, ciphertext));
    assertThrows(ApiException.class, () -> secrets.decrypt(UUID.randomUUID(), ciphertext));
    assertThrows(ApiException.class, () -> secrets.decrypt(id, ciphertext.substring(4)));
  }

  @Test
  void parsesOnlyTheApprovedPostgresUrlOptions() {
    var s =
        PostgresConnectionSpec.parse(
            "postgresql://user:p%40ss%3Aword@localhost:5643/school?sslmode=disable",
            Set.of("localhost"));
    assertEquals("p@ss:word", s.password());
    assertEquals("jdbc:postgresql://localhost:5643/school", s.jdbcUrl());
    assertFalse(s.toString().contains("word"));
    assertEquals(
        "verify-full",
        PostgresConnectionSpec.parse(
                "postgres://reader:abc@db.example.com/school", Set.of("db.example.com"))
            .sslmode());
  }

  @Test
  void blocksUnapprovedHostsJdbcInjectionAndRemotePlaintext() {
    for (String url :
        List.of(
            "postgresql://u:p@169.254.169.254/db",
            "postgresql://u:p@localhost/db?socketFactory=Bad",
            "postgresql://u:p@localhost/db?sslmode=disable&options=bad",
            "jdbc:postgresql://localhost/db",
            "postgresql://u:p@localhost/db%3Foptions%3Dbad",
            "postgresql://u:p@db.example.com/db?sslmode=disable"))
      assertThrows(
          ApiException.class,
          () -> PostgresConnectionSpec.parse(url, Set.of("localhost", "db.example.com")));
  }

  @Test
  void discoveredIdentifiersAreQuotedRatherThanExecuted() {
    assertEquals("\"Student\"\"Records\"", ExternalDatabaseService.quoted("Student\"Records"));
    assertThrows(ApiException.class, () -> ExternalDatabaseService.quoted("bad\0name"));
  }
}
