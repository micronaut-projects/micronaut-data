package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.data.tck.tests.BarBookClient;
import io.micronaut.data.tck.tests.BookDto;
import io.micronaut.data.tck.tests.FooBookClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SQLiteMultitenancyTest {

    @Test
    void testDatasourceMultitenancy() {
        Map<String, Object> properties = new HashMap<>();
        properties.putAll(createDataSourceProperties("foo"));
        properties.putAll(createDataSourceProperties("bar"));
        properties.put("bookRepositoryClass", SQLiteBookRepository.class.getName());
        properties.put("spec.name", "multitenancy");
        properties.put("micronaut.data.multi-tenancy.mode", "DATASOURCE");
        properties.put("micronaut.multitenancy.tenantresolver.httpheader.enabled", "true");

        try (EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, properties, Environment.TEST)) {
            ApplicationContext context = embeddedServer.getApplicationContext();
            FooBookClient fooBookClient = context.getBean(FooBookClient.class);
            BarBookClient barBookClient = context.getBean(BarBookClient.class);

            fooBookClient.deleteAll();
            barBookClient.deleteAll();
            assertEquals(2, context.getBeansOfType(DataSource.class).size());

            BookDto book = fooBookClient.save("The Stand", 1000);
            assertNotNull(book.getId());

            book = fooBookClient.findOne(book.getId()).orElse(null);
            assertNotNull(book);
            assertEquals("The Stand", book.getTitle());
            assertEquals(1, fooBookClient.findAll().size());
            assertEquals(0, barBookClient.findAll().size());
            assertEquals(1, getBooksCount(context.getBean(DataSource.class, io.micronaut.inject.qualifiers.Qualifiers.byName("foo"))));
            assertEquals(0, getBooksCount(context.getBean(DataSource.class, io.micronaut.inject.qualifiers.Qualifiers.byName("bar"))));

            barBookClient.deleteAll();
            assertEquals(1, fooBookClient.findAll().size());

            fooBookClient.deleteAll();
            assertEquals(0, fooBookClient.findAll().size());
        }
    }

    private long getBooksCount(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("select count(*) from book");
             ResultSet resultSet = ps.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> createDataSourceProperties(String dataSourceName) {
        try {
            var databaseFile = Files.createTempFile(dataSourceName.toLowerCase(), ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            String prefix = "datasources." + dataSourceName;
            properties.put(prefix + ".url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put(prefix + ".schema-generate", "CREATE");
            properties.put(prefix + ".dialect", "ANSI");
            properties.put(prefix + ".db-type", "sqlite");
            properties.put(prefix + ".username", "");
            properties.put(prefix + ".password", "");
            properties.put(prefix + ".packages", "io.micronaut.data.jdbc.sqlite,io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities");
            properties.put(prefix + ".driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}
