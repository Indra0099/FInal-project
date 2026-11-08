package com.shardquery.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents", schema = "app")
public class DocumentRecord {
  @Id public UUID id;

  public UUID userId;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false, unique = true, length = 64)
  public String fingerprint;

  @Column(nullable = false, length = 30)
  public String status;

  public Long productId;
  public Long customerId;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  public int attempts;
  public int chunks;

  @Column(columnDefinition = "text")
  public String error;

  @Column(length = 100)
  public String embeddingModel;

  @Column(nullable = false)
  public Instant nextDispatch;
}
