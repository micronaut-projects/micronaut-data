package io.micronaut.data.jdbc.sqlite;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties
class SQLiteSchemaGenerationTest {

    @Inject
    SQLiteOrganizationRepository repository;

    @Disabled(value = "currently UUID not supported for SQLite")
    @Test
    void testUuidGeneratedValue() {
        assertEquals(0L, repository.count());
    }
}
