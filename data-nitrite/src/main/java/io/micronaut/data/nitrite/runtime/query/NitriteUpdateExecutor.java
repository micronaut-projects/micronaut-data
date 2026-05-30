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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.filters.Filter;

/**
 * Executor for Nitrite update operations.
 *
 * @since 1.0.0
 */
@Internal
public final class NitriteUpdateExecutor {

  private static final Pattern SQL_SET_ASSIGNMENT =
      Pattern.compile("(?:\\w+\\.)?(\\w+)\\s*=\\s*:(\\w+)");

  private final NitriteEntityMapper entityMapper;

  /**
   * Create a new update executor.
   *
   * @param entityMapper the entity mapper
   */
  public NitriteUpdateExecutor(@NonNull NitriteEntityMapper entityMapper) {
    this.entityMapper = entityMapper;
  }

  /**
   * Execute a JSON-based update.
   *
   * @param q the prepared query
   * @param filter the Nitrite filter
   * @param updateMap the pre-parsed update map
   * @param jsonParams the bound JSON parameters
   * @param namedParameters the bound named parameters
   * @param collection the Nitrite collection
   * @return the number of affected documents
   */
  public int executeJsonUpdate(
      @NonNull final PreparedQuery<?, ?> q,
      @NonNull final Filter filter,
      @NonNull final Map<String, Object> updateMap,
      @NonNull final Object[] jsonParams,
      @NonNull final Map<String, Object> namedParameters,
      @NonNull final NitriteCollection collection) {
    Object rawSet = updateMap.get("$set");
    if (!(rawSet instanceof Map<?, ?> setFields) || setFields.isEmpty()) {
      return 0;
    }

    Document updateDoc = Document.createDocument();
    for (Map.Entry<?, ?> e : setFields.entrySet()) {
      Object key = e.getKey();
      if (!(key instanceof String fieldName)) {
        continue;
      }
      Object value = e.getValue();
      if (value instanceof String s) {
        if (s.startsWith("$mn_qp:")) {
          try {
            int idx = Integer.parseInt(s.substring(7));
            if (idx >= 0 && idx < jsonParams.length) {
              value = entityMapper.toFilterValue(jsonParams[idx]);
            }
          } catch (NumberFormatException ignored) {
              // ignore invalid JSON parameter index format
          }
        } else if (s.startsWith(":")) {
          String name = s.substring(1);
          if (namedParameters.containsKey(name)) {
            value = namedParameters.get(name);
          }
        }
      }
      updateDoc.put(fieldName, value);
    }

    // Nitrite updates are partial updates using the document fields as the set clause
    var result = collection.update(filter, updateDoc, UpdateOptions.updateOptions(false));
    return result.getAffectedCount();
  }

  /**
   * Parse the SET clause of a SQL-like update statement.
   *
   * @param sql      the SQL string
   * @param params   the positional parameters
   * @param resolver a function to resolve parameter values
   * @return a map of fields to their new values
   */
  @NonNull
  public Map<String, Object> parseSetClause(
      @NonNull final String sql, @Nullable final Object[] params, @NonNull final java.util.function.BiFunction<String, Object[], Object> resolver) {
    int setIdx = sql.toUpperCase(Locale.ROOT).indexOf(" SET ");
    int whereIdx = sql.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
    if (setIdx < 0) {
      return new LinkedHashMap<>();
    }
    String setClause =
        whereIdx >= 0 ? sql.substring(setIdx + 5, whereIdx) : sql.substring(setIdx + 5);
    Map<String, Object> fields = new LinkedHashMap<>();
    Matcher m = SQL_SET_ASSIGNMENT.matcher(setClause);
    while (m.find()) {
      String field = m.group(1);
      String pname = m.group(2);
      if (field != null && pname != null) {
          Object val = resolver.apply(pname, params);
          fields.put(field, val);
      }
    }
    return fields;
  }
}
