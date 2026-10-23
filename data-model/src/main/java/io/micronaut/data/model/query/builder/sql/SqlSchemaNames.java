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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.Internal;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Renders schema names as SQL identifiers, and builds the Oracle statement that creates a schema.
 *
 * @since 5.3.0
 */
@Internal
public final class SqlSchemaNames {

    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * The Oracle SQL reserved words, which an unquoted identifier cannot be.
     *
     * @see <a href="https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/Oracle-SQL-Reserved-Words.html">Oracle SQL Reserved Words</a>
     */
    private static final Set<String> ORACLE_RESERVED_WORDS = Set.of(
        "ACCESS", "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUDIT", "BETWEEN", "BY", "CHAR", "CHECK",
        "CLUSTER", "COLUMN", "COMMENT", "COMPRESS", "CONNECT", "CREATE", "CURRENT", "DATE", "DECIMAL", "DEFAULT",
        "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "EXCLUSIVE", "EXISTS", "FILE", "FLOAT", "FOR", "FROM", "GRANT",
        "GROUP", "HAVING", "IDENTIFIED", "IMMEDIATE", "IN", "INCREMENT", "INDEX", "INITIAL", "INSERT", "INTEGER",
        "INTERSECT", "INTO", "IS", "LEVEL", "LIKE", "LOCK", "LONG", "MAXEXTENTS", "MINUS", "MLSLABEL", "MODE",
        "MODIFY", "NOAUDIT", "NOCOMPRESS", "NOT", "NOWAIT", "NULL", "NUMBER", "OF", "OFFLINE", "ON", "ONLINE",
        "OPTION", "OR", "ORDER", "PCTFREE", "PRIOR", "PUBLIC", "RAW", "RENAME", "RESOURCE", "REVOKE", "ROW",
        "ROWID", "ROWNUM", "ROWS", "SELECT", "SESSION", "SET", "SHARE", "SIZE", "SMALLINT", "START", "SUCCESSFUL",
        "SYNONYM", "SYSDATE", "TABLE", "THEN", "TO", "TRIGGER", "UID", "UNION", "UNIQUE", "UPDATE", "USER",
        "VALIDATE", "VALUES", "VARCHAR", "VARCHAR2", "VIEW", "WHENEVER", "WHERE", "WITH"
    );

    private SqlSchemaNames() {
    }

    /**
     * Renders a schema name as a dialect-compatible SQL identifier. Simple identifiers are left unquoted to
     * preserve the case-folding behavior of existing applications. All other names are quoted and their
     * delimiters escaped. On Oracle, a name that starts with {@code _} or is a reserved word is quoted in
     * upper case, the form Oracle folds an unquoted name to. The input is always treated as a logical name;
     * SQL quoting supplied by a caller is not interpreted.
     *
     * @param dialect The SQL dialect
     * @param name The logical schema name
     * @return The safely rendered identifier
     * @throws IllegalArgumentException If the name is null, empty, or contains a NUL character
     */
    public static String render(Dialect dialect, String name) {
        if (name == null || name.isEmpty() || name.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("Schema name must not be null, empty, or contain a NUL character");
        }
        if (SIMPLE_IDENTIFIER.matcher(name).matches() && !(dialect == Dialect.ORACLE && needsOracleQuotes(name))) {
            return name;
        }
        return switch (dialect) {
            case MYSQL, H2 -> "`" + name.replace("`", "``") + "`";
            // SQL Server bracket-delimited identifiers only use ] as the
            // closing delimiter. A [ inside the name is ordinary content;
            // escaping it would change the schema name.
            case SQL_SERVER -> "[" + name.replace("]", "]]") + "]";
            case ORACLE -> "\"" + oracleUserName(name).replace("\"", "\"\"") + "\"";
            default -> "\"" + name.replace("\"", "\"\"") + "\"";
        };
    }

    /**
     * Builds the Oracle statement that creates a schema. An Oracle schema is a user, so the statement creates a
     * user without credentials ({@code NO AUTHENTICATION}) and grants it quota on the database default
     * tablespace. An existing user is accepted, so the statement can run on every startup, but a role of the
     * same name, which Oracle reports with the same error (ORA-01920), is not.
     *
     * @param name The logical schema name
     * @return The PL/SQL block creating the schema
     * @throws IllegalArgumentException If the name is null, empty, or contains a NUL character
     */
    public static String oracleCreateSchemaStatement(String name) {
        String identifier = render(Dialect.ORACLE, name).replace("'", "''");
        String userName = oracleUserName(name).replace("'", "''");
        return """
            DECLARE
              ts VARCHAR2(128);
              users NUMBER;
            BEGIN
              SELECT property_value INTO ts FROM database_properties WHERE property_name = 'DEFAULT_PERMANENT_TABLESPACE';
              EXECUTE IMMEDIATE 'CREATE USER %s NO AUTHENTICATION QUOTA UNLIMITED ON ' || DBMS_ASSERT.ENQUOTE_NAME(ts, FALSE);
            EXCEPTION
              WHEN OTHERS THEN
                IF SQLCODE != -1920 THEN
                  RAISE;
                END IF;
                -- ORA-01920 also means that a role has the name: only an existing user is the schema
                SELECT COUNT(*) INTO users FROM all_users WHERE username = '%s';
                IF users = 0 THEN
                  RAISE;
                END IF;
            END;""".formatted(identifier, userName);
    }

    private static boolean needsOracleQuotes(String name) {
        return name.charAt(0) == '_' || ORACLE_RESERVED_WORDS.contains(name.toUpperCase(Locale.ENGLISH));
    }

    private static String oracleUserName(String name) {
        // Oracle folds unquoted names to upper case, and quoted names are rendered in that form too
        return name.toUpperCase(Locale.ENGLISH);
    }
}
