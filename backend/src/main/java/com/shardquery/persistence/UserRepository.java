package com.shardquery.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserRecord, UUID> {
  Optional<UserRecord> findByEmail(String email);
}
