package com.shardquery.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_tables", schema = "app")
public class UserTable {
  @Id public UUID id;

  @Column(nullable = false)
  public UUID databaseId;

  @Column(nullable = false, length = 63)
  public String name;

  @Column(nullable = false, length = 63)
  public String physicalName;

  @Column(nullable = false, columnDefinition = "text")
  public String columnsJson;

  @Column(length = 63)
  public String remoteSchema;

  @Column(nullable = false)
  public Instant createdAt;
}
