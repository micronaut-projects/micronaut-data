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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@SQLiteDBProperties
@Property(name = "sqlite.sql-recorder.enabled", value = "true")
class SQLiteReturningTest {

    @Inject
    SQLiteBookRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void insertUpdateAndDeleteReturnAffectedRow() {
        Book book = new Book();
        book.setTitle("SQLite Returning");
        book.setTotalPages(100);

        RecordedSql.clear();
        Book inserted = repository.insertReturning(book);

        assertTrue(RecordedSql.hasStatementContaining("INSERT", "RETURNING"), RecordedSql.statements().toString());
        assertNotSame(book, inserted);
        assertEquals("SQLite Returning", inserted.getTitle());
        assertEquals(100, inserted.getTotalPages());
        assertEquals("SQLite Returning", repository.findById(inserted.getId()).orElseThrow().getTitle());

        inserted.setTitle("SQLite Returning Updated");

        RecordedSql.clear();
        Book updated = repository.updateReturning(inserted);

        assertTrue(RecordedSql.hasStatementContaining("UPDATE", "RETURNING"), RecordedSql.statements().toString());
        assertNotSame(inserted, updated);
        assertEquals(inserted.getId(), updated.getId());
        assertEquals("SQLite Returning Updated", updated.getTitle());
        assertEquals("SQLite Returning Updated", repository.findById(updated.getId()).orElseThrow().getTitle());

        RecordedSql.clear();
        Book deleted = repository.deleteReturning(updated);

        assertTrue(RecordedSql.hasStatementContaining("DELETE", "RETURNING"), RecordedSql.statements().toString());
        assertEquals(updated.getId(), deleted.getId());
        assertEquals("SQLite Returning Updated", deleted.getTitle());
        assertFalse(repository.existsById(updated.getId()));
    }
}
