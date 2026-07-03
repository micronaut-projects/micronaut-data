package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.connection.jdbc.advice.ContextualConnection;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Person;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
@SQLiteDBProperties
@Property(name = "datasources.other.name", value = "otherdb")
@Property(name = "datasources.other.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.other.dialect", value = "SQLITE")
@Property(name = "datasources.other.db-type", value = "sqlite")
@Property(name = "datasources.other.packages", value = "io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities,io.micronaut.data.jdbc.sqlite")
@Property(name = "datasources.other.driverClassName", value = "org.sqlite.JDBC")
@Property(name = "datasources.other.url", value = "jdbc:sqlite:file:other?mode=memory&cache=shared")
@Property(name = "datasources.other.username", value = "")
@Property(name = "datasources.other.password", value = "")
class MultipleDataSourceTest {

    @Inject
    SQLitePersonRepository personRepository;

    @Inject
    OtherPersonRepository otherPersonRepository;

    @Inject
    DbService service;

    @AfterEach
    void cleanup() {
        personRepository.deleteAll();
        otherPersonRepository.deleteAll();
    }

    @Test
    void testMultipleDataSources() {
        personRepository.save(person("Fred"));
        personRepository.save(person("Bob"));

        assertEquals(2, personRepository.count());
        assertEquals(0, otherPersonRepository.count());

        otherPersonRepository.save(person("Joe"));

        assertEquals("Joe", otherPersonRepository.findAll().get(0).getName());

        otherPersonRepository.saveTwoOtherDb(person("One"), person("Two"));
        otherPersonRepository.saveTwoOtherDb2(person("Three"), person("Four"));

        assertEquals(5, otherPersonRepository.count());
        assertDoesNotThrow(service::save);
    }

    private static Person person(String name) {
        Person person = new Person();
        person.setName(name);
        return person;
    }

    @Singleton
    static class DbService {

        private final Connection defaultConnection;
        private final Connection otherConnection;
        private final SQLitePersonRepository personRepository;
        private final OtherPersonRepository otherPersonRepository;
        private final ApplicationEventPublisher<Person> eventPublisher;
        private final List<Person> personsSaved = new ArrayList<>();

        DbService(Connection defaultConnection,
                  @Named("other") Connection otherConnection,
                  SQLitePersonRepository personRepository,
                  OtherPersonRepository otherPersonRepository,
                  ApplicationEventPublisher<Person> eventPublisher) {
            this.defaultConnection = defaultConnection;
            this.otherConnection = otherConnection;
            this.personRepository = personRepository;
            this.otherPersonRepository = otherPersonRepository;
            this.eventPublisher = eventPublisher;
        }

        @TransactionalEventListener
        void savedListenerDefault(Person person) {
            add(person);
        }

        @TransactionalEventListener(transactionManager = "other")
        void savedListenerOther(Person person) {
            add(person);
        }

        private void add(Person person) {
            boolean alreadyAdded = personsSaved.stream().anyMatch(saved -> saved.getId().equals(person.getId()));
            if (!alreadyAdded) {
                personsSaved.add(person);
            }
        }

        void save() {
            if (!personsSaved.isEmpty()) {
                throw new IllegalStateException("Expected no saved persons before transaction test");
            }
            saveTx1();
            if (personsSaved.size() != 2) {
                throw new IllegalStateException("Expected two transactional events");
            }
            if (!"Two".equals(personsSaved.get(0).getName())) {
                throw new IllegalStateException("Expected other transaction event first");
            }
            if (!"One".equals(personsSaved.get(1).getName())) {
                throw new IllegalStateException("Expected default transaction event second");
            }
        }

        @Transactional
        void saveTx1() {
            Person person = person("One");
            personRepository.save(person);
            eventPublisher.publishEvent(person);
            saveTx2();
            if (personsSaved.size() != 1) {
                throw new IllegalStateException("Expected nested transaction event to be visible before outer commit");
            }
            if (!"Two".equals(personsSaved.get(0).getName())) {
                throw new IllegalStateException("Expected nested transaction event first");
            }
        }

        @Transactional("other")
        void saveTx2() {
            Person person = person("Two");
            otherPersonRepository.save(person);
            eventPublisher.publishEvent(person);
            Connection unwrappedDefaultConnection = unwrap(defaultConnection);
            Connection unwrappedOtherConnection = unwrap(otherConnection);
            if (unwrappedDefaultConnection == unwrappedOtherConnection) {
                throw new IllegalStateException("Expected separate data source connections");
            }
        }

        private Connection unwrap(Connection connection) {
            try {
                return connection.unwrap(Connection.class);
            } catch (SQLException e) {
                throw new IllegalStateException("Unable to unwrap connection", e);
            }
        }
    }

    @JdbcRepository(dataSource = "other", dialect = Dialect.SQLITE)
    static abstract class OtherPersonRepository implements CrudRepository<Person, Long> {

        private final JdbcOperations jdbcOperations;
        private final Connection otherConnection;

        OtherPersonRepository(@Named("other") JdbcOperations jdbcOperations,
                              @Named("default") Connection defaultConnection,
                              @Named("other") Connection otherConnection) {
            this.jdbcOperations = jdbcOperations;
            this.otherConnection = otherConnection;
            assertTrue(defaultConnection instanceof ContextualConnection);
            assertTrue(otherConnection instanceof ContextualConnection);
        }

        @Transactional("other")
        void saveTwoOtherDb(Person one, Person two) {
            saveTwo(one, two);
        }

        @Transactional(transactionManager = "other")
        void saveTwoOtherDb2(Person one, Person two) {
            saveTwo(one, two);
        }

        void saveTwo(Person one, Person two) {
            Connection jdbcOperationsConnection = unwrap(jdbcOperations.getConnection());
            Connection unwrappedOtherConnection = unwrap(otherConnection);
            if (jdbcOperationsConnection != unwrappedOtherConnection) {
                throw new IllegalStateException("Expected JDBC operations to use the other connection");
            }
            jdbcOperations.prepareStatement("INSERT INTO `person` (`enabled`,`age`,`name`) VALUES (?,?,?)", statement -> {
                statement.setBoolean(1, one.isEnabled());
                statement.setInt(2, one.getAge());
                statement.setString(3, one.getName());
                statement.addBatch();
                statement.clearParameters();
                statement.setBoolean(1, two.isEnabled());
                statement.setInt(2, two.getAge());
                statement.setString(3, two.getName());
                statement.addBatch();
                return statement.executeBatch();
            });
        }

        private Connection unwrap(Connection connection) {
            try {
                return connection.unwrap(Connection.class);
            } catch (SQLException e) {
                throw new IllegalStateException("Unable to unwrap connection", e);
            }
        }
    }
}
