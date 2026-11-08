package com.shardquery;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardquery.ai.CitationValidator;
import com.shardquery.query.QueryTypes.Source;
import com.shardquery.web.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;

class CitationValidatorTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<Source> sources =
      List.of(
          new Source("D1", UUID.randomUUID(), "policy.txt", 1, "Policy", .9, null, null),
          new Source("D2", UUID.randomUUID(), "review.txt", 1, "Review", .8, 201L, 101L));

  String validate(String answer, List<String> citations) {
    return CitationValidator.validate(
        mapper.valueToTree(Map.of("answer", answer, "citations", citations)), sources);
  }

  @Test
  void acceptsCitedSubsetAndRepeatedReferences() {
    assertEquals("First [D1]. Second [D1].", validate("First [D1]. Second [D1].", List.of("D1")));
  }

  @Test
  void normalizesGroupedReferencesWithoutChangingTheirIdentity() {
    assertEquals("Compared [D1][D2].", validate("Compared [D1, D2].", List.of("D1", "D2")));
  }

  @Test
  void rejectsUnknownInlineAndDeclaredReferences() {
    assertThrows(ApiException.class, () -> validate("Claim [D1, D999]", List.of("D1")));
    assertThrows(ApiException.class, () -> validate("Claim [D1]", List.of("D1", "D999")));
  }

  @Test
  void rejectsMissingMismatchedAndUncitedEvidence() {
    assertThrows(ApiException.class, () -> validate("Claim [D1]", List.of("D2")));
    assertThrows(ApiException.class, () -> validate("Claim [D1]", List.of()));
    assertThrows(ApiException.class, () -> validate("Uncited claim", List.of()));
  }

  @Test
  void rejectsEmptyOrMalformedAnswers() {
    assertThrows(ApiException.class, () -> validate(" ", List.of()));
    assertThrows(
        ApiException.class,
        () ->
            CitationValidator.validate(
                mapper.valueToTree(Map.of("answer", "Claim [D1]", "citations", "D1")), sources));
  }

  @Test
  void allowsDatabaseOnlyAnswersWithoutDocumentCitations() {
    assertEquals(
        "Three database records.",
        CitationValidator.validate(
            mapper.valueToTree(Map.of("answer", "Three database records.", "citations", List.of())),
            List.of()));
  }
}
