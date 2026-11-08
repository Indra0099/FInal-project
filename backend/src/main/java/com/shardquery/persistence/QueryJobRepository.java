package com.shardquery.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryJobRepository extends JpaRepository<QueryJob, UUID> {
  List<QueryJob> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<QueryJob> findByIdAndUserId(UUID id, UUID userId);

  List<QueryJob> findByStatusIn(Collection<String> statuses);
}
