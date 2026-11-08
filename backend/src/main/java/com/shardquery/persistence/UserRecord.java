package com.shardquery.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "app")
public class UserRecord {
  @Id public UUID id;

  @Column(nullable = false, length = 80)
  public String name;

  @Column(nullable = false, unique = true, length = 254)
  public String email;

  @JsonIgnore
  @Column(nullable = false, length = 100)
  public String passwordHash;

  @Column(nullable = false)
  public Instant createdAt;
}
