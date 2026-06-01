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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executor for Nitrite update operations.
 *
 * @since 1.0.0
 */
@Internal
public final class NitriteUpdateExecutor {

  private static final Pattern SQL_SET_ASSIGNMENT =
      Pattern.compile("(?:\\w+\\.)?(\\w+)\\s*=\\s*:(\\w+)");

    /**
   * Create a new update executor.
   *
     */
  public NitriteUpdateExecutor() {
  }

    /**
   * Parse the SET clause of a SQL-like update statement.
   *
   * @param sql the SQL string
   * @param params the positional parameters
   * @param resolver a function to resolve parameter values
   * @return a map of fields to their new values
   */
  public Map<String, Object> parseSetClause(
      final String sql, final Object[] params, final java.util.function.BiFunction<String, Object[], Object> resolver) {
    int setIdx = sql.toUpperCase().indexOf(" SET ");
    int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
    if (setIdx < 0) {
      return Map.of();
    }
    String setClause =
        whereIdx >= 0 ? sql.substring(setIdx + 5, whereIdx) : sql.substring(setIdx + 5);
    Map<String, Object> fields = new LinkedHashMap<>();
    Matcher m = SQL_SET_ASSIGNMENT.matcher(setClause);
    while (m.find()) {
      String field = m.group(1);
      String pname = m.group(2);
      Object val = resolver.apply(pname, params);
      fields.put(field, val);
    }
    return fields;
  }
}
