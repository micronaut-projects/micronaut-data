/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Nitrite JSON and SQL-like query strings.
 *
 * @since 1.0.0
 */
@Internal
public final class NitriteQueryParser {

  /**
   * Parse a JSON query string into a Map/List structure.
   *
   * @param jsonStr the JSON string
   * @return the parsed object
   */
  public Object parseJson(final String jsonStr) {
    String json = jsonStr.trim();
    if (json.equals("{}")) {
      return Map.of();
    }
    if (json.startsWith("{")) {
      return parseJsonObject(json);
    }
    if (json.startsWith("[")) {
      return parseJsonArray(json);
    }
    throw new IllegalArgumentException("Invalid JSON: " + json);
  }

  /**
   * Parse a JSON object string into a Map.
   *
   * @param json the JSON object string
   * @return the parsed map
   */
  private Map<String, Object> parseJsonObject(String json) {
    Map<String, Object> result = new LinkedHashMap<>();
    json = json.trim();
    if (json.equals("{}")) {
      return result;
    }

    // Remove outer braces
    json = json.substring(1, json.length() - 1).trim();

    int i = 0;
    while (i < json.length()) {
      // Skip whitespace
      while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
        i++;
      }
      if (i >= json.length()) {
        break;
      }

      // Parse key
      if (json.charAt(i) != '"') {
        throw new IllegalArgumentException("Expected '\"' at index " + i + " in [" + json + "], found '" + json.charAt(i) + "'");
      }
      i++;
      int keyStart = i;
      while (i < json.length() && json.charAt(i) != '"') {
        if (json.charAt(i) == '\\') {
          i++;
        }
        i++;
      }
      String key = json.substring(keyStart, i);
      i++; // skip closing quote

      // Skip to colon
      while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
        i++;
      }
      if (i < json.length() && json.charAt(i) == ':') {
        i++;
      }
      // Skip whitespace after colon
      while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
        i++;
      }

      // Parse value
      Object value;
      if (i >= json.length()) {
        break;
      }
      char c = json.charAt(i);
      if (c == '"') {
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length() && json.charAt(i) != '"') {
          if (json.charAt(i) == '\\') {
            i++;
            if (i < json.length()) {
              char escaped = json.charAt(i);
              switch (escaped) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                default -> sb.append(escaped);
              }
            }
          } else {
            sb.append(json.charAt(i));
          }
          i++;
        }
        value = sb.toString();
        i++; // skip closing quote
      } else if (c == '{') {
        int start = i;
        int depth = 1;
        i++;
        while (i < json.length() && depth > 0) {
          if (json.charAt(i) == '{') {
            depth++;
          }
          if (json.charAt(i) == '}') {
            depth--;
          }
          i++;
        }
        value = parseJsonObject(json.substring(start, i));
      } else if (c == '[') {
        int start = i;
        int depth = 1;
        i++;
        while (i < json.length() && depth > 0) {
          if (json.charAt(i) == '[') {
            depth++;
          }
          if (json.charAt(i) == ']') {
            depth--;
          }
          i++;
        }
        value = parseJsonArray(json.substring(start, i));
      } else if (c == 't' || c == 'f') {
        // boolean or potential placeholder
        int start = i;
        while (i < json.length() && (Character.isLetter(json.charAt(i)) || json.charAt(i) == ':')) {
          i++;
        }
        String s = json.substring(start, i);
        if (s.startsWith(":") || s.startsWith("$mn_qp:")) {
          value = s;
        } else if (s.equals("true") || s.equals("false")) {
          value = Boolean.parseBoolean(s);
        } else {
          value = s;
        }
      } else if (c == ':') {
        // Bare named parameter: :title
        int start = i;
        while (i < json.length() && (Character.isLetterOrDigit(json.charAt(i)) || json.charAt(i) == ':')) {
          i++;
        }
        value = json.substring(start, i);
      } else if (c == '$') {
        // Bare placeholder: $mn_qp:0
        int start = i;
        while (i < json.length() && (Character.isLetterOrDigit(json.charAt(i)) || json.charAt(i) == ':' || json.charAt(i) == '$' || json.charAt(i) == '_')) {
          i++;
        }
        value = json.substring(start, i);
      } else if (c == 'n') {
        // null
        i += 4;
        value = null;
      } else {
        // number or potential placeholder
        int start = i;
        while (i < json.length()
            && (Character.isDigit(json.charAt(i))
                || Character.isLetter(json.charAt(i))
                || json.charAt(i) == '.'
                || json.charAt(i) == '-'
                || json.charAt(i) == ':'
                || json.charAt(i) == '$')) {
          i++;
        }
        String s = json.substring(start, i).trim();
        if (s.startsWith("$mn_qp:") || s.startsWith(":")) {
          value = s;
        } else if (s.equals("true")) {
          value = true;
        } else if (s.equals("false")) {
          value = false;
        } else if (s.equals("null")) {
          value = null;
        } else if (s.contains(".")) {
          try {
            value = Double.parseDouble(s);
          } catch (NumberFormatException e) {
            value = s;
          }
        } else {
          try {
            value = Integer.parseInt(s);
          } catch (NumberFormatException e) {
            value = s;
          }
        }
      }

      result.put(key, value);

      // Skip comma
      while (i < json.length()
          && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) {
        i++;
      }
    }

    return result;
  }

  /**
   * Parse a JSON array string into a List.
   *
   * @param json the JSON array string
   * @return the parsed list
   */
  private List<Object> parseJsonArray(String json) {
    List<Object> result = new ArrayList<>();
    json = json.trim();
    if (json.equals("[]")) {
      return result;
    }

    // Remove outer brackets
    json = json.substring(1, json.length() - 1).trim();

    int i = 0;
    while (i < json.length()) {
      // Skip whitespace and commas
      while (i < json.length()
          && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) {
        i++;
      }
      if (i >= json.length()) {
        break;
      }

      char c = json.charAt(i);
      Object value;
      if (c == '"') {
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length() && json.charAt(i) != '"') {
          if (json.charAt(i) == '\\') {
            i++;
            if (i < json.length()) {
              char escaped = json.charAt(i);
              switch (escaped) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                default -> sb.append(escaped);
              }
            }
          } else {
            sb.append(json.charAt(i));
          }
          i++;
        }
        value = sb.toString();
        i++; // skip closing quote
      } else if (c == '{') {
        int start = i;
        int depth = 1;
        i++;
        while (i < json.length() && depth > 0) {
          if (json.charAt(i) == '{') {
            depth++;
          }
          if (json.charAt(i) == '}') {
            depth--;
          }
          i++;
        }
        value = parseJsonObject(json.substring(start, i));
      } else if (c == '[') {
        int start = i;
        int depth = 1;
        i++;
        while (i < json.length() && depth > 0) {
          if (json.charAt(i) == '[') {
            depth++;
          }
          if (json.charAt(i) == ']') {
            depth--;
          }
          i++;
        }
        value = parseJsonArray(json.substring(start, i));
      } else {
        int start = i;
        while (i < json.length()
            && (Character.isDigit(json.charAt(i))
                || Character.isLetter(json.charAt(i))
                || json.charAt(i) == '.'
                || json.charAt(i) == '-'
                || json.charAt(i) == ':'
                || json.charAt(i) == '$')) {
          i++;
        }
        String s = json.substring(start, i).trim();
        if (s.startsWith("$mn_qp:") || s.startsWith(":")) {
          value = s;
        } else if (s.equals("true")) {
          value = true;
        } else if (s.equals("false")) {
          value = false;
        } else if (s.equals("null")) {
          value = null;
        } else if (s.contains(".")) {
          try {
            value = Double.parseDouble(s);
          } catch (NumberFormatException e) {
            value = s;
          }
        } else {
          try {
            value = Integer.parseInt(s);
          } catch (NumberFormatException e) {
            value = s;
          }
        }
      }

      result.add(value);
    }

    return result;
  }

  /**
   * Parse the SELECT clause from a SQL-like query to extract field names for projection.
   * <p>
   * Example usage in repository method:
   * <pre>{@code
   * @Query("SELECT name FROM Person WHERE active = true")
   * List<String> findActivePersonNames();
   *
   * @Query("SELECT id, name FROM Person ORDER BY name")
   * List<PersonName> findAllPersonNames();
   * }</pre>
   *
   * @param sql the SQL query string
   * @return list of field names to project, or null if no SELECT clause found
   */
  public List<String> parseSelectClause(final String sql) {
    if (sql == null || sql.trim().isEmpty()) {
      return null;
    }
    String upper = sql.trim().toUpperCase();
    if (!upper.startsWith("SELECT ")) {
      return null;
    }
    
    // Find FROM clause to isolate SELECT fields
    int fromIdx = upper.indexOf(" FROM ");
    if (fromIdx < 0) {
      return null;
    }
    
    // Extract fields between SELECT and FROM
    String fieldsPart = sql.trim().substring(7, fromIdx).trim();
    
    // Handle SELECT * - return null to indicate no projection
    if (fieldsPart.equals("*")) {
      return null;
    }
    
    // Split by comma and extract field names
    List<String> fields = new ArrayList<>();
    for (String field : fieldsPart.split(",")) {
      String f = field.trim();
      // Handle "table.field" or "field" or "field AS alias"
      int spaceIdx = f.indexOf(' ');
      if (spaceIdx > 0) {
        // Check for AS keyword
        String rest = f.substring(spaceIdx).trim().toUpperCase();
        if (rest.startsWith("AS ")) {
          f = f.substring(0, spaceIdx).trim();
        } else {
          // Just take the first part (field name)
          f = f.substring(0, spaceIdx).trim();
        }
      }
      // Extract just the field name if it's qualified (table.field)
      int dotIdx = f.lastIndexOf('.');
      if (dotIdx >= 0) {
        f = f.substring(dotIdx + 1).trim();
      }
      // Remove any quotes or backticks
      f = f.replaceAll("[\"`]", "");
      if (!f.isEmpty()) {
        fields.add(f);
      }
    }
    
    return fields.isEmpty() ? null : fields;
  }

  /**
   * Extract the projection field from a JSON query that uses $project syntax.
   * <p>
   * Example usage in repository method:
   * <pre>{@code
   * // JSON with $project syntax:
   * @Query("{\"$project\": \"name\", \"active\": {\"$eq\": true}}")
   * List<String> findActivePersonNames();
   *
   * // SQL SELECT syntax (alternative):
   * @Query("SELECT name FROM Person WHERE active = true")
   * List<String> findActivePersonNames();
   * }</pre>
   *
   * @param jsonQuery the JSON query string
   * @return the field name to project, or null if not using $project syntax
   */
  public String extractProjectionField(String jsonQuery) {
    if (jsonQuery == null || !jsonQuery.trim().startsWith("{")) {
      return null;
    }
    try {
      Object parsed = parseJson(jsonQuery);
      if (parsed instanceof Map) {
        Map<?, ?> map = (Map<?, ?>) parsed;
        if (map.containsKey("$project")) {
          Object projectValue = map.get("$project");
          if (projectValue instanceof String) {
            return (String) projectValue;
          }
        }
      }
    } catch (Exception e) {
      // Ignore parsing errors
    }
    return null;
  }

  /**
   * Check if a JSON query uses $project syntax.
   *
   * @param jsonQuery the JSON query string
   * @return true if using $project syntax
   */
  public boolean hasProjection(String jsonQuery) {
    return extractProjectionField(jsonQuery) != null;
  }
}
