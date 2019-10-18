package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2WhereAnnotationSpec extends Specification {

    @Inject
    H2EnabledPersonRepository personRepository

    void "test return only enabled people"() {
        given:
        personRepository.saveAll([
                new Person(name: "Fred", age:35),
                new Person(name: "Joe", age:30, enabled: false),
                new Person(name: "Bob", age:30)
        ])

        expect:
        personRepository.count() == 2
        personRepository.countByNameLike("%e%") == 1
        !personRepository.findAll().any({ it.name == "Joe" })

    }
}
