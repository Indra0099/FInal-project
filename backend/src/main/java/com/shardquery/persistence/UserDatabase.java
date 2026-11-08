package com.shardquery.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_databases", schema = "app")
public class UserDatabase {
  @Id public UUID id;

  @Column(nullable = false)
  public UUID userId;

  @Column(nullable = false, length = 60)
  public String name;

  @Column(nullable = false, length = 63)
  public String physicalName;

  @Column(nullable = false, length = 20)
  public String status;

  @Column(nullable = false, length = 16)
  public String kind = "LOCAL";

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(columnDefinition = "text")
  public String connectionSecret;

  @Column(length = 63)
  public String remoteSchema;

  @Column(columnDefinition = "text")
  public String connectionInfo;

  @Column(nullable = false)
  public Instant createdAt;
}
