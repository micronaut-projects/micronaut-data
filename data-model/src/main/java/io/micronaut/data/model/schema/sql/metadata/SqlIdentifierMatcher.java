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
package io.micronaut.data.model.schema.sql.metadata;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.IdentifierNamingStrategy;

import java.util.Locale;

/**
 * Matches the mapped names (schemas, tables, sequences and columns) to the names stored in the database, the same way as
 * the generated SQL refers to them.
 * <p>
 * A mapped name is resolved to the stored name first: an unescaped name is stored in the database identifier case, and so is
 * an escaped name for Oracle, whose quoted names are upper case, and for H2, which converts the backtick quoted names like
 * the unquoted ones. The other dialects store an escaped name as declared.
 * <p>
 * The resolved and stored names are then compared by their keys, exactly when the database names are case-sensitive and
 * in lower case otherwise.
 *
 * @param dialect The dialect
 * @param namingStrategy The case the database stores the unquoted identifiers in
 * @param caseSensitiveTables Whether the schema, table and sequence names are case-sensitive
 * @param caseSensitiveColumns Whether the column names are case-sensitive
 * @author radovanradic
 * @since 5.3.0
 */
@Internal
public record SqlIdentifierMatcher(Dialect dialect,
                                   IdentifierNamingStrategy namingStrategy,
                                   boolean caseSensitiveTables,
                                   boolean caseSensitiveColumns) {

    private static final int POSTGRES_MAX_IDENTIFIER_BYTES = 63;

    private static final SqlIdentifierMatcher CASE_INSENSITIVE = new SqlIdentifierMatcher(Dialect.ANSI, IdentifierNamingStrategy.MIXED, false, false);

    /**
     * @return The matcher comparing all the names case-insensitively
     */
    public static SqlIdentifierMatcher caseInsensitive() {
        return CASE_INSENSITIVE;
    }

    /**
     * Creates the matcher for a database. The databases storing the unquoted identifiers in upper or lower case compare
     * the names exactly, since a quoted name can differ from an unquoted one only by the case. The databases storing
     * mixed case identifiers typically compare them case-insensitively, except the MySQL table names, which are case-sensitive
     * when the database reports it ({@code lower_case_table_names=0}).
     *
     * @param dialect The dialect
     * @param namingStrategy The case the database stores the unquoted identifiers in
     * @param mixedCaseIdentifiers Whether the database reports the unquoted mixed case identifiers as case-sensitive,
     * see {@link java.sql.DatabaseMetaData#supportsMixedCaseIdentifiers()}
     * @return The matcher
     */
    public static SqlIdentifierMatcher of(Dialect dialect, IdentifierNamingStrategy namingStrategy, boolean mixedCaseIdentifiers) {
        if (dialect == Dialect.MYSQL) {
            // The MySQL column names are never case-sensitive
            return new SqlIdentifierMatcher(dialect, namingStrategy, mixedCaseIdentifiers, false);
        }
        boolean caseSensitive = namingStrategy != IdentifierNamingStrategy.MIXED;
        return new SqlIdentifierMatcher(dialect, namingStrategy, caseSensitive, caseSensitive);
    }

    /**
     * Resolves a mapped name to the name stored in the database.
     *
     * @param name The mapped name
     * @param escape Whether the mapping escapes the names
     * @return The name as stored in the database
     */
    public String resolve(String name, boolean escape) {
        String resolved = (escape && dialect != Dialect.ORACLE && dialect != Dialect.H2) ? name : namingStrategy.apply(name);
        // PostgreSQL truncates the longer identifiers when creating and when querying
        return dialect == Dialect.POSTGRES ? truncatePostgresIdentifier(resolved) : resolved;
    }

    /**
     * PostgreSQL truncates the identifiers to 63 bytes (UTF-8 database encoding) without splitting a multibyte character.
     *
     * @param identifier The identifier
     * @return The identifier as stored by PostgreSQL
     */
    public static String truncatePostgresIdentifier(String identifier) {
        int bytes = 0;
        int index = 0;
        while (index < identifier.length()) {
            int codePoint = identifier.codePointAt(index);
            int codePointBytes = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
            if (bytes + codePointBytes > POSTGRES_MAX_IDENTIFIER_BYTES) {
                break;
            }
            bytes += codePointBytes;
            index += Character.charCount(codePoint);
        }
        return identifier.substring(0, index);
    }

    /**
     * @param storedName The schema, table or sequence name as stored in the database
     * @return The key of the name
     */
    public String tableKey(String storedName) {
        return caseSensitiveTables ? storedName : storedName.toLowerCase(Locale.ENGLISH);
    }

    /**
     * @param name The mapped schema, table or sequence name
     * @param escape Whether the mapping escapes the names
     * @return The key of the name
     */
    public String mappedTableKey(String name, boolean escape) {
        return tableKey(resolve(name, escape));
    }

    /**
     * @param storedName The column name as stored in the database
     * @return The key of the name
     */
    public String columnKey(String storedName) {
        return caseSensitiveColumns ? storedName : storedName.toLowerCase(Locale.ENGLISH);
    }

    /**
     * @param name The mapped column name
     * @param escape Whether the mapping escapes the names
     * @return The key of the name
     */
    public String mappedColumnKey(String name, boolean escape) {
        return columnKey(resolve(name, escape));
    }
}
