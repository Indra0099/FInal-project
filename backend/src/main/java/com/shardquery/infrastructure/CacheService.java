package com.shardquery.infrastructure;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CacheService {
  private final StringRedisTemplate redis;

  public CacheService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public String get(String key) {
    try {
      return redis.opsForValue().get("sq:" + key);
    } catch (Exception e) {
      return null;
    }
  }

  public void put(String key, String value, int seconds) {
    try {
      redis.opsForValue().set("sq:" + key, value, Duration.ofSeconds(seconds));
    } catch (Exception ignored) {
    }
  }

  public boolean healthy() {
    try (var connection = redis.getConnectionFactory().getConnection()) {
      return "PONG".equals(connection.ping());
    } catch (Exception e) {
      return false;
    }
  }
}
