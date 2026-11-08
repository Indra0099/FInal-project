package com.shardquery.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTableRepository extends JpaRepository<UserTable, UUID> {
  List<UserTable> findByDatabaseIdOrderByCreatedAtAsc(UUID databaseId);

  Optional<UserTable> findByIdAndDatabaseId(UUID id, UUID databaseId);

  boolean existsByDatabaseIdAndName(UUID databaseId, String name);

  long countByDatabaseId(UUID databaseId);
}
