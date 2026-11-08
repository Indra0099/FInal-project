package com.shardquery.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ErrorHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e) {
    return error(e.status, e.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return error(
        400,
        e instanceof IllegalArgumentException
            ? e.getMessage()
            : "Invalid request. Check the supplied fields.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return error(
        400,
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .findFirst()
            .orElse("Check the supplied fields."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> size(Exception e) {
    return error(413, "Files must be at most 10 MB.");
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class
  })
  ResponseEntity<?> missing(Exception e) {
    return error(400, "A required request field is missing.");
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<?> media(Exception e) {
    return error(415, "Unsupported request content type.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unknown(Exception e) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .warn("Request failed: {}", e.getClass().getSimpleName());
    return error(
        503,
        "A service is unavailable or the file could not be processed. Check service status and try"
            + " again.");
  }

  private ResponseEntity<?> error(int status, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("status", status, "message", message == null ? "Invalid request" : message));
  }
}
