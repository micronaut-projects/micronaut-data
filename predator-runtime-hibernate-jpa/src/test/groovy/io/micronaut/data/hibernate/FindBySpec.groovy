package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Pageable
import io.micronaut.test.annotation.MicronautTest
import org.hibernate.SessionFactory
import spock.lang.Specification

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class FindBySpec extends Specification {

    @Inject
    DataSource dataSource

    @Inject
    SessionFactory sessionFactory

    @Inject
    PersonRepository personRepository

    void "test setup"() {
        expect:
        dataSource != null
        sessionFactory != null
    }

    void "test find by name"() {
        when:
        Person p = personRepository.findByName("Fred")

        then:
        p == null
        !personRepository.findOptionalByName("Fred").isPresent()

        when:
        sessionFactory.currentSession.persist(new Person(name: "Fred"))
        sessionFactory.currentSession.persist(new Person(name: "Bob"))
        sessionFactory.currentSession.persist(new Person(name: "Fredrick"))
        p = personRepository.findByName("Bob")

        then:
        p != null
        p.name == "Bob"
        personRepository.findOptionalByName("Bob").isPresent()

        when:
        def results = personRepository.findAllByName("Bob")

        then:
        results.size() == 1
        results[0].name == 'Bob'

        when:
        results = personRepository.findAllByNameLike("Fred%", Pageable.from(0, 10))

        then:
        results.size() == 2

        when:
        results = personRepository.findAllByNameLike("Fred%", Pageable.from(1, 10))

        then:
        results.size() == 1
    }
}
