package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.annotation.TransactionalAdvice
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import javax.transaction.Transactional

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
@H2DBProperties
@Property(name = "datasources.other.name", value = "otherdb")
@Property(name = "datasources.other.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.other.dialect", value = "H2")
class MultipleDataSourceSpec extends Specification {

    @Inject H2PersonRepository personRepository
    @Inject OtherPersonRepository otherPersonRepository

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
        otherPersonRepository.saveTwo(
                new Person(name:"One"),
                new Person(name:"Two")
        )

        then:
        otherPersonRepository.count() == 3
    }


    @JdbcRepository(dialect = Dialect.H2)
    @Repository('other')
    static abstract class OtherPersonRepository implements CrudRepository<Person, Long> {

        private final JdbcOperations jdbcOperations;

        OtherPersonRepository(@Named("other") JdbcOperations jdbcOperations) {
            this.jdbcOperations = jdbcOperations;
        }

        @Transactional
        @TransactionalAdvice("other")
        void saveTwo(Person one, Person two) {
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