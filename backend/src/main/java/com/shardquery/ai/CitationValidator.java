package com.shardquery.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.shardquery.query.QueryTypes.Source;
import com.shardquery.web.ApiException;
import java.util.*;
import java.util.regex.Pattern;

/** Checks reference identity and format; it cannot prove that a cited claim is true. */
public final class CitationValidator {
  private static final Pattern GROUP = Pattern.compile("\\[(D\\d+(?:\\s*,\\s*D\\d+)*)\\]");

  private CitationValidator() {}

  public static String validate(JsonNode result, List<Source> sources) {
    if (!result.path("answer").isTextual()
        || result.path("answer").asText().isBlank()
        || !result.path("citations").isArray())
      throw new ApiException(502, "The model returned an invalid evidence answer. Please retry.");
    Set<String> valid = new HashSet<>();
    sources.forEach(s -> valid.add(s.id()));
    Set<String> declared = new HashSet<>();
    for (var citation : result.path("citations")) {
      if (!citation.isTextual() || !valid.contains(citation.asText()))
        throw new ApiException(502, "The model cited an unknown source. Please retry.");
      declared.add(citation.asText());
    }

    var matcher = GROUP.matcher(result.path("answer").asText());
    var normalized = new StringBuilder();
    Set<String> mentioned = new HashSet<>();
    while (matcher.find()) {
      var replacement = new StringBuilder();
      for (String id : matcher.group(1).split("\\s*,\\s*")) {
        if (!valid.contains(id))
          throw new ApiException(502, "The model cited an unknown source. Please retry.");
        mentioned.add(id);
        replacement.append('[').append(id).append(']');
      }
      matcher.appendReplacement(normalized, replacement.toString());
    }
    matcher.appendTail(normalized);
    if (!mentioned.equals(declared))
      throw new ApiException(
          502, "The model citation list does not match the answer. Please retry.");
    if (!sources.isEmpty() && mentioned.isEmpty())
      throw new ApiException(502, "The model did not cite the supplied documents. Please retry.");
    return normalized.toString();
  }
}
