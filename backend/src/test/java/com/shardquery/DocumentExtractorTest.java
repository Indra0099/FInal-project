package com.shardquery;

import static org.junit.jupiter.api.Assertions.*;

import com.shardquery.document.DocumentExtractor;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentExtractorTest {
  DocumentExtractor extractor = new DocumentExtractor();

  @Test
  void extractsUtf8AndRejectsBinary() {
    assertDoesNotThrow(
        () -> assertEquals("hello", extractor.extract("hello".getBytes(), "notes.txt")));
    assertThrows(Exception.class, () -> extractor.extract(new byte[] {(byte) 0xff}, "bad.txt"));
    assertThrows(
        IllegalArgumentException.class, () -> extractor.extract("hello".getBytes(), "fake.pdf"));
    assertThrows(
        IllegalArgumentException.class, () -> extractor.extract("hello".getBytes(), "app.exe"));
  }

  @Test
  void chunksCoverStartAndEndWithOverlap() {
    String text = "word ".repeat(1000);
    var chunks = extractor.chunks(text);
    assertTrue(chunks.size() > 1);
    assertTrue(chunks.getFirst().startsWith(text.substring(0, 100)));
    assertTrue(chunks.getLast().endsWith(text.substring(text.length() - 100)));
    String a = chunks.getFirst();
    assertTrue(chunks.get(1).startsWith(a.substring(a.length() - 180)));
  }

  @Test
  void refusesExcessiveChunkCount() {
    assertThrows(IllegalArgumentException.class, () -> extractor.chunks("x".repeat(200000)));
  }

  @Test
  void readsRealDocxBytes() throws Exception {
    try (var doc = new XWPFDocument();
        var out = new ByteArrayOutputStream()) {
      doc.createParagraph().createRun().setText("Laptop return policy");
      doc.write(out);
      assertTrue(
          extractor.extract(out.toByteArray(), "policy.docx").contains("Laptop return policy"));
    }
  }

  @Test
  void readsRealPdfBytes() throws Exception {
    try (var doc = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var content = new PDPageContentStream(doc, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 700);
        content.showText("Fictional warranty policy");
        content.endText();
      }
      doc.save(out);
      assertTrue(
          extractor.extract(out.toByteArray(), "policy.pdf").contains("Fictional warranty policy"));
    }
  }
}
