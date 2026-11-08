package com.shardquery;

import static com.shardquery.workspace.PersonalQueryEngine.*;
import static com.shardquery.workspace.WorkspaceTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import com.shardquery.web.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;

class PersonalQueryEngineTest {
  final UUID student = UUID.randomUUID(), fee = UUID.randomUUID();
  final List<Table> catalog =
      List.of(
          new Table(
              student,
              UUID.randomUUID(),
              "Students",
              "student",
              List.of(
                  new Column("id", FieldType.TEXT, true),
                  new Column("name", FieldType.TEXT, true))),
          new Table(
              fee,
              UUID.randomUUID(),
              "Students fees",
              "student fees",
              List.of(
                  new Column("student_id", FieldType.TEXT, true),
                  new Column("fees", FieldType.INTEGER, true))));

  Plan plan() {
    return new Plan(
        "PERSONAL",
        List.of(student, fee),
        List.of(new Join(student, "id", fee, "student_id")),
        List.of(new Filter(fee, "fees", "GT", "150000")),
        new Sort(fee, "fees", true),
        1,
        "Find students above the fees threshold");
  }

  @Test
  void joinsOnStudentIdThenFiltersSortsAndLimitsNumerically() {
    var data =
        Map.of(
            student,
            List.of(
                Map.<String, Object>of("id", "S1", "name", "Asha"),
                Map.<String, Object>of("id", "S2", "name", "Ravi"),
                Map.<String, Object>of("id", "S3", "name", "Mira")),
            fee,
            List.of(
                Map.<String, Object>of("student_id", "S1", "fees", "150000"),
                Map.<String, Object>of("student_id", "S2", "fees", "220000"),
                Map.<String, Object>of("student_id", "S3", "fees", "900000")));
    var rows = evaluate(validate(plan(), catalog), data);
    assertEquals(1, rows.size());
    assertEquals("Mira", rows.getFirst().get("Students / student.name"));
    assertEquals("900000", rows.getFirst().get("Students fees / student fees.fees"));
  }

  @Test
  void rejectsUnknownOrOtherAccountTablesBeforeExecution() {
    var p = plan();
    assertEquals(
        422,
        assertThrows(
                ApiException.class,
                () ->
                    validate(
                        new Plan(
                            "PERSONAL",
                            List.of(UUID.randomUUID()),
                            List.of(),
                            List.of(),
                            null,
                            50,
                            "bad"),
                        catalog))
            .status);
  }

  @Test
  void rejectsInventedColumnsAndMissingJoin() {
    assertThrows(
        ApiException.class,
        () ->
            validate(
                new Plan("PERSONAL", List.of(student, fee), List.of(), List.of(), null, 50, "bad"),
                catalog));
    assertThrows(
        ApiException.class,
        () ->
            validate(
                new Plan(
                    "PERSONAL",
                    List.of(student),
                    List.of(),
                    List.of(new Filter(student, "id; DROP TABLE x", "EQ", "1")),
                    null,
                    50,
                    "bad"),
                catalog));
  }

  @Test
  void treatsSqlLookingTextAsData() {
    var p =
        new Plan(
            "PERSONAL",
            List.of(student),
            List.of(),
            List.of(new Filter(student, "name", "CONTAINS", "'; drop table")),
            null,
            50,
            "text");
    var row = Map.<String, Object>of("id", "x", "name", "O'Brien'; DROP TABLE students; --");
    assertEquals(List.of(row), evaluate(validate(p, catalog), Map.of(student, List.of(row))));
  }

  @Test
  void nullJoinKeysDoNotMatch() {
    var a = new HashMap<String, Object>();
    a.put("id", null);
    a.put("name", "Missing");
    var b = new HashMap<String, Object>();
    b.put("student_id", null);
    b.put("fees", "220000");
    assertTrue(
        evaluate(validate(plan(), catalog), Map.of(student, List.of(a), fee, List.of(b)))
            .isEmpty());
  }

  @Test
  void rejectsOversizeReadsInsteadOfPretendingResultsAreComplete() {
    var rows = Collections.nCopies(1001, Map.<String, Object>of("id", "x", "name", "A"));
    assertThrows(
        ApiException.class,
        () -> evaluate(validate(plan(), catalog), Map.of(student, rows, fee, List.of())));
  }

  @Test
  void boundsManyToManyFanout() {
    var a = Collections.nCopies(80, Map.<String, Object>of("id", "same", "name", "A"));
    var b = Collections.nCopies(80, Map.<String, Object>of("student_id", "same", "fees", "200000"));
    assertThrows(
        ApiException.class, () -> evaluate(validate(plan(), catalog), Map.of(student, a, fee, b)));
  }
}
