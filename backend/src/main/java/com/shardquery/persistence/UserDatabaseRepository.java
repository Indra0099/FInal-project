package com.shardquery.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDatabaseRepository extends JpaRepository<UserDatabase, UUID> {
  List<UserDatabase> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<UserDatabase> findByIdAndUserId(UUID id, UUID userId);

  long countByUserId(UUID userId);

  boolean existsByUserIdAndName(UUID userId, String name);
}
