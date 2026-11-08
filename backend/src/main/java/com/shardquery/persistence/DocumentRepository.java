package com.shardquery.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentRecord, UUID> {
  Optional<DocumentRecord> findByFingerprint(String fingerprint);

  List<DocumentRecord> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserId(UUID userId);

  Optional<DocumentRecord> findByIdAndUserId(UUID id, UUID userId);

  List<DocumentRecord> findByUserIdAndStatus(UUID userId, String status);

  List<DocumentRecord> findTop10ByStatusAndNextDispatchBeforeOrderByCreatedAtAsc(
      String status, java.time.Instant now);

  List<DocumentRecord> findByStatus(String status);
}
