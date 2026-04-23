package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SqliteSchemaValidationTest {

    @Test
    void validateSchema() {
        Map<String, Object> props = createProperties();

        ApplicationContext initialContext = ApplicationContext.run(props);
        try {
            props.put("datasources.default.schema-generate", "validate");
            assertDoesNotThrow(() -> {
                try (ApplicationContext validationContext = ApplicationContext.run(props)) {
                }
            });
        } finally {
            initialContext.close();
        }
    }

    @Test
    void validateSchemaForTckSchemaEntities() {
        Map<String, Object> props = createProperties();
        props.put("datasources.default.packages", "io.micronaut.data.tck.entities.schema");

        ApplicationContext initialContext = ApplicationContext.run(props);
        try {
            props.put("datasources.default.schema-generate", "validate");
            assertDoesNotThrow(() -> {
                try (ApplicationContext validationContext = ApplicationContext.run(props)) {
                }
            });
        } finally {
            initialContext.close();
        }
    }

    private Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("sqliteschema", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "ANSI");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}
