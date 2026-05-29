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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Oracle SQL {@code RETURNING} column metadata with both canonical and alias-expanded views.
 *
 * @param canonicalColumnIndexesByName The canonical returned column names mapped to the underlying JDBC/R2DBC index
 * @param columnIndexesByName The canonical names plus Oracle-specific aliases mapped to the same underlying index
 * @author Radovan Radic
 * @since 5.0
 */
@Internal
public record OracleReturningMetadata(Map<String, Integer> canonicalColumnIndexesByName,
                                      Map<String, Integer> columnIndexesByName) {

    public OracleReturningMetadata {
        canonicalColumnIndexesByName = Collections.unmodifiableMap(new LinkedHashMap<>(canonicalColumnIndexesByName));
        columnIndexesByName = Collections.unmodifiableMap(new LinkedHashMap<>(columnIndexesByName));
    }

    /**
     * Creates Oracle {@code RETURNING} metadata using zero-based positions derived from the provided column order.
     *
     * @param columnNames The returned column names in result order
     * @return The Oracle returning metadata
     */
    public static OracleReturningMetadata create(List<String> columnNames) {
        Map<String, Integer> canonicalColumnIndexesByName = new LinkedHashMap<>(columnNames.size());
        for (int i = 0; i < columnNames.size(); i++) {
            canonicalColumnIndexesByName.put(columnNames.get(i), i);
        }
        return create(canonicalColumnIndexesByName);
    }

    /**
     * Creates Oracle {@code RETURNING} metadata using the provided column order and underlying indexes.
     *
     * @param columnNames The returned column names in result order
     * @param columnIndexes The underlying JDBC/R2DBC indexes for each returned column
     * @return The Oracle returning metadata
     */
    public static OracleReturningMetadata create(List<String> columnNames, List<Integer> columnIndexes) {
        if (columnNames.size() != columnIndexes.size()) {
            throw new IllegalArgumentException("Oracle RETURNING column metadata must have matching names and indexes");
        }
        Map<String, Integer> canonicalColumnIndexesByName = new LinkedHashMap<>(columnNames.size());
        for (int i = 0; i < columnNames.size(); i++) {
            canonicalColumnIndexesByName.put(columnNames.get(i), columnIndexes.get(i));
        }
        return create(canonicalColumnIndexesByName);
    }

    private static OracleReturningMetadata create(Map<String, Integer> canonicalColumnIndexesByName) {
        Map<String, Integer> columnIndexesByName = new LinkedHashMap<>(canonicalColumnIndexesByName.size());
        for (Map.Entry<String, Integer> entry : canonicalColumnIndexesByName.entrySet()) {
            addAliases(columnIndexesByName, entry.getKey(), entry.getValue());
        }
        return new OracleReturningMetadata(canonicalColumnIndexesByName, columnIndexesByName);
    }

    private static void addAliases(Map<String, Integer> columnIndexesByName,
                                   String columnName,
                                   int columnIndex) {
        columnIndexesByName.putIfAbsent(columnName, columnIndex);
        String unquotedColumnName = unquoteColumnName(columnName);
        columnIndexesByName.putIfAbsent(unquotedColumnName, columnIndex);
        columnIndexesByName.putIfAbsent(unquotedColumnName.toLowerCase(Locale.ENGLISH), columnIndex);
        columnIndexesByName.putIfAbsent(unquotedColumnName.toUpperCase(Locale.ENGLISH), columnIndex);
    }

    private static String unquoteColumnName(String columnName) {
        if (columnName.length() > 1) {
            char quote = columnName.charAt(0);
            if ((quote == '"' || quote == '`') && columnName.charAt(columnName.length() - 1) == quote) {
                return columnName.substring(1, columnName.length() - 1);
            }
        }
        return columnName;
    }
}
