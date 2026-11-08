package com.shardquery.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_jobs", schema = "app")
public class QueryJob {
  @Id public UUID id;

  public UUID userId;

  @Column(nullable = false, columnDefinition = "text")
  public String question;

  @Column(nullable = false, length = 20)
  public String mode;

  @Column(nullable = false, length = 30)
  public String status;

  @Column(nullable = false)
  public Instant createdAt;

  public Instant completedAt;

  @Column(columnDefinition = "text")
  public String resultJson;

  @Column(columnDefinition = "text")
  public String error;
}
