package io.micronaut.data.jdbc.sqlite;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@JavaSQLiteDBProperties
class SQLiteSchemaGenerationTest {

    @Inject
    SQLiteOrganizationRepository repository;

    @Test
    void testUuidGeneratedValue() {
        assertEquals(0L, repository.count());
    }
}
