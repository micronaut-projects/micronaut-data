package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.data.jdbc.config.SchemaGenerator;
import io.micronaut.data.tck.entities.Person;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SQLiteEagerContextTest {

    @Test
    void testEagerStart() {
        Map<String, Object> properties = new HashMap<>(createSqliteDataSourceProperties("default"));
        properties.put("eager-test", true);
        properties.putAll(createSqliteDataSourceProperties("other"));

        try (ApplicationContext context = ApplicationContext.builder(properties)
            .eagerInitSingletons(true)
            .start()) {
            SQLitePersonRepository personRepository = context.getBean(SQLitePersonRepository.class);
            assertEquals(4, personRepository.findAll().size());
        }
    }

    private static Map<String, Object> createSqliteDataSourceProperties(String dataSourceName) {
        try {
            var databaseFile = Files.createTempFile(dataSourceName.toLowerCase(Locale.ENGLISH), ".sqlite").toFile();
            databaseFile.deleteOnExit();
            String prefix = "datasources." + dataSourceName;
            Map<String, Object> properties = new HashMap<>();
            properties.put(prefix + ".url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put(prefix + ".schema-generate", "CREATE");
            properties.put(prefix + ".dialect", "SQLITE");
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

    @Singleton
    static class SimpleService {

        private final SQLitePersonRepository personRepository;

        SimpleService(SQLitePersonRepository personRepository) {
            this.personRepository = personRepository;
        }

        @PostConstruct
        void init(SchemaGenerator schemaGenerator, BeanLocator beanLocator) {
            schemaGenerator.createOrValidateSchema(beanLocator);

            personRepository.save(newPerson("a"));
            personRepository.save(newPerson("c"));
            personRepository.save(newPerson("b"));
            personRepository.save(newPerson("d"));
        }

        private Person newPerson(String name) {
            Person person = new Person();
            person.setName(name);
            return person;
        }
    }
}
