package com.shardquery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.shardquery.ai.GeminiGateway;
import com.shardquery.document.*;
import com.shardquery.infrastructure.CacheService;
import com.shardquery.persistence.*;
import com.shardquery.web.ApiException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.kafka.core.KafkaTemplate;

class DocumentRetryTest {
  @TempDir Path directory;

  @Test
  @SuppressWarnings("unchecked")
  void indexingStopsAfterThreeFailuresAndManualRetryResetsAttempts() throws Exception {
    var repository = mock(DocumentRepository.class);
    var ai = mock(GeminiGateway.class);
    var vectors = mock(VectorStore.class);
    var service =
        new DocumentService(
            repository,
            new DocumentExtractor(),
            ai,
            vectors,
            mock(KafkaTemplate.class),
            mock(CacheService.class),
            directory.toString());
    var doc = new DocumentRecord();
    doc.id = UUID.randomUUID();
    doc.name = "retry-test.txt";
    doc.userId = UUID.randomUUID();
    when(repository.findByIdAndUserId(doc.id, doc.userId)).thenReturn(Optional.of(doc));
    doc.status = "QUEUED";
    Files.writeString(directory.resolve(doc.id + ".bin"), "Fictional review for a retry test.");
    when(repository.findById(doc.id)).thenReturn(Optional.of(doc));
    when(repository.save(doc)).thenAnswer(invocation -> invocation.getArgument(0));
    when(ai.embed(anyList())).thenThrow(new ApiException(503, "Provider unavailable."));

    for (int attempt = 1; attempt <= 3; attempt++) {
      var before = Instant.now();
      service.consume(doc.id.toString());
      assertEquals(attempt, doc.attempts);
      assertEquals(attempt == 3 ? "FAILED" : "QUEUED", doc.status);
      assertFalse(doc.nextDispatch.isBefore(before.plusSeconds(10L * attempt)));
      assertEquals("Provider unavailable.", doc.error);
    }
    service.consume(doc.id.toString());
    verify(ai, times(3)).embed(anyList());
    verifyNoInteractions(vectors);

    service.retry(doc.id, doc.userId);
    assertEquals("QUEUED", doc.status);
    assertEquals(0, doc.attempts);
    assertNull(doc.error);
  }

  @Test
  @SuppressWarnings("unchecked")
  void readyDocumentsAndMalformedMessagesNeverReindex() throws Exception {
    var repository = mock(DocumentRepository.class);
    var ai = mock(GeminiGateway.class);
    var vectors = mock(VectorStore.class);
    var service =
        new DocumentService(
            repository,
            new DocumentExtractor(),
            ai,
            vectors,
            mock(KafkaTemplate.class),
            mock(CacheService.class),
            directory.toString());
    var doc = new DocumentRecord();
    doc.id = UUID.randomUUID();
    doc.status = "READY";
    when(repository.findById(doc.id)).thenReturn(Optional.of(doc));
    service.consume("invalid-message");
    service.consume(doc.id.toString());
    verifyNoInteractions(ai, vectors);
    verify(repository, never()).save(any());
  }
}
