package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Property(name = "datasources.other.url", value = "jdbc:h2:mem:otherDB")
@Property(name = 'jpa.other.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class MultipleDataSourceSpec extends Specification {

    @Inject PersonCrudRepository personRepository
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
    }


    @Repository('other')
    static interface OtherPersonRepository extends CrudRepository<Person, Long>{

    }
}
