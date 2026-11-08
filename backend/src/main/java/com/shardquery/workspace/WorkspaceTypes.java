package com.shardquery.workspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public final class WorkspaceTypes {
  private WorkspaceTypes() {}

  public enum FieldType {
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE
  }

  public record Column(
      @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,30}") String name,
      @NotNull FieldType type,
      boolean required) {}

  public record CreateDatabase(
      @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9 _-]{0,59}") String name) {}

  public record CreateTable(
      @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9 _-]{0,59}") String name,
      @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Column> columns) {}

  public record Rename(@NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9 _-]{0,59}") String name) {}

  public record RenameColumn(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,30}") String name) {}

  public record Confirmation(@NotBlank @Size(max = 100) String confirmation) {}

  public record ConnectDatabase(
      @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9 _-]{0,59}") String name,
      @NotBlank @Size(max = 2048) String connectionUrl,
      @NotBlank @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]{0,62}") String schema) {
    @Override
    public String toString() {
      return "ConnectDatabase[credentials redacted]";
    }
  }

  public record Row(@NotNull @Size(max = 20) Map<String, Object> values) {}
}
