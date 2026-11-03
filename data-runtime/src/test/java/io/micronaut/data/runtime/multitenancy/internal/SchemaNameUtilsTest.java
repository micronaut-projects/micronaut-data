/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.multitenancy.internal;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlSchemaNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaNameUtilsTest {

    @Test
    void simpleNamesArePreservedForEveryDialectButOracle() {
        for (Dialect dialect : Dialect.values()) {
            if (dialect != Dialect.ORACLE) {
                assertEquals("tenant_1", SchemaNameUtils.render(dialect, "tenant_1"));
            }
        }
    }

    @Test
    void namesRequiringQuotingUseTheDialectDelimiter() {
        assertEquals("`tenant-a`", SchemaNameUtils.render(Dialect.H2, "tenant-a"));
        assertEquals("\"tenant-a\"", SchemaNameUtils.render(Dialect.POSTGRES, "tenant-a"));
        assertEquals("\"TENANT-A\"", SchemaNameUtils.render(Dialect.ORACLE, "tenant-a"));
        assertEquals("\"tenant-a\"", SchemaNameUtils.render(Dialect.ANSI, "tenant-a"));
        assertEquals("\"tenant-a\"", SchemaNameUtils.render(Dialect.SQLITE, "tenant-a"));
        assertEquals("`tenant-a`", SchemaNameUtils.render(Dialect.MYSQL, "tenant-a"));
        assertEquals("[tenant-a]", SchemaNameUtils.render(Dialect.SQL_SERVER, "tenant-a"));
    }

    @Test
    void delimitersAreEscapedForEveryDialect() {
        assertEquals("`tenant\"a`", SchemaNameUtils.render(Dialect.H2, "tenant\"a"));
        assertEquals("\"TENANT\"\"A\"", SchemaNameUtils.render(Dialect.ORACLE, "tenant\"a"));
        assertEquals("`tenant``a`", SchemaNameUtils.render(Dialect.MYSQL, "tenant`a"));
        assertEquals("[tenant]]a]", SchemaNameUtils.render(Dialect.SQL_SERVER, "tenant]a"));
    }

    @Test
    void injectionPayloadIsRenderedAsOneIdentifier() {
        String payload = "PUBLIC; CREATE TABLE PWNED(id int); --";

        assertEquals("`PUBLIC; CREATE TABLE PWNED(id int); --`",
            SchemaNameUtils.render(Dialect.H2, payload));
        assertEquals("`PUBLIC; CREATE TABLE PWNED(id int); --`",
            SchemaNameUtils.render(Dialect.MYSQL, payload));
        assertEquals("[PUBLIC; CREATE TABLE PWNED(id int); --]",
            SchemaNameUtils.render(Dialect.SQL_SERVER, payload));
    }

    @Test
    void alreadyQuotedInputIsNotInterpretedAsSql() {
        assertEquals("`\"tenant-a\"`", SchemaNameUtils.render(Dialect.H2, "\"tenant-a\""));
    }

    @Test
    void oracleQuotesEveryNameInUpperCase() {
        assertEquals("\"TENANT_1\"", SchemaNameUtils.render(Dialect.ORACLE, "tenant_1"));
        assertEquals("\"ORDER\"", SchemaNameUtils.render(Dialect.ORACLE, "order"));
        assertEquals("\"EXCEPT\"", SchemaNameUtils.render(Dialect.ORACLE, "except"));
        assertEquals("\"USER\"", SchemaNameUtils.render(Dialect.ORACLE, "User"));
        assertEquals("\"_TENANT\"", SchemaNameUtils.render(Dialect.ORACLE, "_tenant"));
        assertEquals("order", SchemaNameUtils.render(Dialect.POSTGRES, "order"));
        assertEquals("_tenant", SchemaNameUtils.render(Dialect.H2, "_tenant"));
    }

    @Test
    void emptyAndNulNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SchemaNameUtils.render(Dialect.H2, null));
        assertThrows(IllegalArgumentException.class, () -> SchemaNameUtils.render(Dialect.H2, ""));
        assertThrows(IllegalArgumentException.class, () -> SchemaNameUtils.render(Dialect.H2, "tenant\u0000a"));
    }

    @Test
    void oracleCreateSchemaCreatesAUserWithQuota() {
        String sql = SchemaNameUtils.oracleCreateSchemaStatement("tenant_1");

        assertTrue(sql.contains("EXECUTE IMMEDIATE 'CREATE USER \"TENANT_1\" NO AUTHENTICATION QUOTA UNLIMITED ON '"));
        assertTrue(sql.contains("IF SQLCODE != -1920 THEN"));
        assertFalse(sql.contains("CREATE DATABASE"));
    }

    @Test
    void oracleCreateSchemaOnlyAcceptsAnExistingUser() {
        String sql = SchemaNameUtils.oracleCreateSchemaStatement("tenant_1");

        assertTrue(sql.contains("SELECT COUNT(*) INTO users FROM all_users WHERE username = 'TENANT_1';"));
        assertTrue(sql.contains("IF users = 0 THEN"));
    }

    @Test
    void oracleCreateSchemaKeepsAPlaceholderInARenderedIdentifier() {
        String quoted = SqlSchemaNames.oracleCreateSchemaStatement("\"${tenant.schema}\"", true);
        String unquoted = SqlSchemaNames.oracleCreateSchemaStatement("${tenant.schema}", false);

        assertTrue(quoted.contains("'CREATE USER \"${tenant.schema}\" NO AUTHENTICATION"));
        assertTrue(quoted.contains("WHERE username = '${tenant.schema}';"));
        assertTrue(unquoted.contains("'CREATE USER ${tenant.schema} NO AUTHENTICATION"));
        assertTrue(unquoted.contains("WHERE username = UPPER('${tenant.schema}');"));
    }

    @Test
    void oracleCreateSchemaQuotesReservedWords() {
        String sql = SchemaNameUtils.oracleCreateSchemaStatement("order");

        assertTrue(sql.contains("'CREATE USER \"ORDER\" NO AUTHENTICATION"));
        assertTrue(sql.contains("WHERE username = 'ORDER';"));
    }

    @Test
    void oracleCreateSchemaEscapesTheIdentifierInsideTheLiteral() {
        String sql = SchemaNameUtils.oracleCreateSchemaStatement("tenant'a");

        assertTrue(sql.contains("'CREATE USER \"TENANT''A\" NO AUTHENTICATION"));
        assertTrue(sql.contains("WHERE username = 'TENANT''A';"));
    }
}
