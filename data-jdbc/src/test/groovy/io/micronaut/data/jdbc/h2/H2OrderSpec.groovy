package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2OrderSpec extends Specification {

    @Inject
    H2PersonRepository personRepository

    void "test order - case insensitive"() {
        given: 'some test data is created'
        personRepository.save(new Person(name: 'ABC4'))
        personRepository.save(new Person(name: 'abc3'))
        personRepository.save(new Person(name: 'abc2'))
        personRepository.save(new Person(name: 'ABC1'))

        when: 'the list is sorted with ignore case'
        def order = new Sort.Order("name", Sort.Order.Direction.ASC, true)
        def list = personRepository.list(Pageable.from(0, 10).order(order))

        then: 'the list is ordered with case insensitive sorting'
        list[0].name == 'ABC1'
        list[1].name == 'abc2'
        list[2].name == 'abc3'
        list[3].name == 'ABC4'
    }
}
