package com.shardquery.infrastructure;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  @Bean
  NewTopic documentsTopic() {
    return TopicBuilder.name("shardquery.documents").partitions(1).replicas(1).build();
  }
}
