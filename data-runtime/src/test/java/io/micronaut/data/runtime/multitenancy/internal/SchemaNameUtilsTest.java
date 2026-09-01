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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaNameUtilsTest {

    @Test
    void simpleNamesArePreservedForEveryDialect() {
        for (Dialect dialect : Dialect.values()) {
            assertEquals("tenant_1", SchemaNameUtils.render(dialect, "tenant_1"));
        }
    }

    @Test
    void namesRequiringQuotingUseTheDialectDelimiter() {
        assertEquals("`tenant-a`", SchemaNameUtils.render(Dialect.H2, "tenant-a"));
        assertEquals("\"tenant-a\"", SchemaNameUtils.render(Dialect.POSTGRES, "tenant-a"));
        assertEquals("\"TENANT-A\"", SchemaNameUtils.render(Dialect.ORACLE, "tenant-a"));
        assertEquals("\"tenant-a\"", SchemaNameUtils.render(Dialect.ANSI, "tenant-a"));
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
    void emptyAndNulNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SchemaNameUtils.render(Dialect.H2, null));
        assertThrows(IllegalArgumentException.class, () -> SchemaNameUtils.render(Dialect.H2, ""));
        assertThrows(IllegalArgumentException.class, () -> SchemaNameUtils.render(Dialect.H2, "tenant\u0000a"));
    }
}
