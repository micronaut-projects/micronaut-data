package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.annotation.TransactionalAdvice
import io.micronaut.transaction.annotation.TransactionalEventListener
import io.micronaut.transaction.jdbc.TransactionalConnection
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import java.sql.Connection

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
@H2DBProperties
@Property(name = "datasources.other.name", value = "otherdb")
@Property(name = "datasources.other.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.other.dialect", value = "H2")
@Property(name = "datasources.other.packages", value = "io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities,io.micronaut.data.jdbc.h2")
// This properties can be eliminated after TestResources bug is fixed
@Property(name = "datasources.other.driverClassName", value = "org.h2.Driver")
@Property(name = "datasources.other.url", value = "jdbc:h2:mem:other;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name = "datasources.other.username", value = "")
@Property(name = "datasources.other.password", value = "")
class MultipleDataSourceSpec extends Specification {

    @Inject
    H2PersonRepository personRepository
    @Inject
    OtherPersonRepository otherPersonRepository
    @Inject
    DbService service

    void cleanup() {
        personRepository.deleteAll()
        otherPersonRepository.deleteAll()
    }

    void "test multiple data sources"() {
        when:
            personRepository.save(new Person(name: "Fred"))
            personRepository.save(new Person(name: "Bob"))

        then:
            personRepository.count() == 2
            otherPersonRepository.count() == 0

        when:
            otherPersonRepository.save(new Person(name: "Joe"))

        then:
            otherPersonRepository.findAll().toList()[0].name == "Joe"

        when:
            otherPersonRepository.saveTwoOtherDb(
                    new Person(name: "One"),
                    new Person(name: "Two")
            )
            otherPersonRepository.saveTwoOtherDb2(
                    new Person(name: "Three"),
                    new Person(name: "Four")
            )

        then:
            otherPersonRepository.count() == 5

        when:
            service.save()
        then:
            noExceptionThrown()
    }

    @Singleton
    static class DbService {

        private final Connection defaultConnection;
        private final Connection otherConnection;
        private final H2PersonRepository personRepository
        private final OtherPersonRepository otherPersonRepository
        private final ApplicationEventPublisher<Person> eventPublisher

        private List<Person> personsSaved = new ArrayList<>()

        DbService(Connection defaultConnection,
                  @Named("other") Connection otherConnection,
                  H2PersonRepository personRepository,
                  OtherPersonRepository otherPersonRepository,
                  ApplicationEventPublisher<Person> eventPublisher) {
            this.defaultConnection = defaultConnection
            this.otherConnection = otherConnection
            this.personRepository = personRepository
            this.otherPersonRepository = otherPersonRepository
            this.eventPublisher = eventPublisher
        }

        @TransactionalEventListener
        void savedListenerDefault(Person person) {
            add(person)
        }

        @TransactionalEventListener(transactionManager = "other")
        void savedListenerOther(Person person) {
            add(person)
        }

        private boolean add(Person person) {
            if (personsSaved.stream().anyMatch(p -> p.id == person.id)) {
                return
            }
            personsSaved.add(person)
        }

        void save() {
            assert personsSaved.size() == 0
            saveTx1()
            assert personsSaved.size() == 2
            assert personsSaved[0].name == "Two"
            assert personsSaved[1].name == "One"
        }

        @TransactionalAdvice
        void saveTx1() {
            def person = new Person(name: "One")
            personRepository.save(person)
            eventPublisher.publishEvent(person)
            saveTx2()
            assert personsSaved.size() == 1
            assert personsSaved[0].name == "Two"
        }

        @TransactionalAdvice("other")
        void saveTx2() {
            def person = new Person(name: "Two")
            otherPersonRepository.save(person)
            eventPublisher.publishEvent(person)
            def unwrappedDefaultConnection = defaultConnection.unwrap(Connection.class)
            def unwrappedOtherConnection = otherConnection.unwrap(Connection.class)
            if (unwrappedDefaultConnection == unwrappedOtherConnection) {
                throw new IllegalStateException()
            }
        }

    }


    @JdbcRepository(dataSource = "other", dialect = Dialect.H2)
    static abstract class OtherPersonRepository implements CrudRepository<Person, Long> {

        private final JdbcOperations jdbcOperations;
        private final Connection defaultConnection;
        private final Connection otherConnection;

        OtherPersonRepository(@Named("other") JdbcOperations jdbcOperations,
                              @Named("default") Connection defaultConnection,
                              @Named("other") Connection otherConnection) {
            this.jdbcOperations = jdbcOperations
            this.defaultConnection = defaultConnection
            this.otherConnection = otherConnection
            assert defaultConnection instanceof TransactionalConnection
            assert otherConnection instanceof TransactionalConnection
        }

        @TransactionalAdvice("other")
        void saveTwoOtherDb(Person one, Person two) {
            saveTwo(one, two)
        }

        @TransactionalAdvice(transactionManager = "other")
        void saveTwoOtherDb2(Person one, Person two) {
            saveTwo(one, two)
        }

        void saveTwo(Person one, Person two) {
            def jdbcOperationsConnection = jdbcOperations.getConnection().unwrap(Connection.class)
            def unwrappedOtherConnection = otherConnection.unwrap(Connection.class)
            if (jdbcOperationsConnection != unwrappedOtherConnection) {
                throw new IllegalStateException()
            }
            jdbcOperations.prepareStatement("INSERT INTO `person` (`enabled`,`age`,`name`) VALUES (?,?,?)", {
                it.setBoolean(1, one.isEnabled())
                it.setInt(2, one.getAge())
                it.setString(3, one.getName())
                it.addBatch()
                it.clearParameters()
                it.setBoolean(1, two.isEnabled())
                it.setInt(2, two.getAge())
                it.setString(3, two.getName())
                it.addBatch()
                it.executeBatch()
            })
        }
    }
}
