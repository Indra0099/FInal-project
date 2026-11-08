package com.shardquery.document;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class DocumentExtractor {
  public String extract(byte[] bytes, String name) throws IOException {
    if (bytes.length > 10 * 1024 * 1024)
      throw new IllegalArgumentException("Files must be at most 10 MB.");
    String text;
    name = name.toLowerCase(Locale.ROOT);
    if (name.endsWith(".pdf")) {
      if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
        throw new IllegalArgumentException("This is not a valid PDF file.");
      try (var doc = Loader.loadPDF(bytes)) {
        if (doc.getNumberOfPages() > 100)
          throw new IllegalArgumentException("PDFs are limited to 100 pages.");
        text = new PDFTextStripper().getText(doc);
      }
    } else if (name.endsWith(".docx")) {
      ZipSecureFile.setMaxEntrySize(20 * 1024 * 1024);
      ZipSecureFile.setMinInflateRatio(0.01);
      try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes));
          var extractor = new XWPFWordExtractor(doc)) {
        text = extractor.getText();
      }
    } else if (name.endsWith(".txt")) {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
    } else throw new IllegalArgumentException("Upload a PDF, DOCX or UTF-8 TXT file.");
    text = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").strip();
    if (text.isBlank())
      throw new IllegalArgumentException(
          "No readable text found. Scanned PDFs need OCR before upload.");
    if (text.length() > 200000)
      throw new IllegalArgumentException("Extracted text must be under 200,000 characters.");
    return text;
  }

  public List<String> chunks(String text) {
    var chunks = new ArrayList<String>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(text.length(), start + 1400);
      if (end < text.length()) {
        int boundary = text.lastIndexOf(' ', end);
        if (boundary > start + 1000) end = boundary;
      }
      chunks.add(text.substring(start, end));
      if (chunks.size() > 100)
        throw new IllegalArgumentException(
            "Document exceeds the 100-chunk limit. Split it into smaller documents.");
      if (end == text.length()) break;
      start = end - 180;
    }
    return chunks;
  }
}
