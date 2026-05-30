package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.mongoport.entities.NitriteProjPerson
import io.micronaut.data.nitrite.mongoport.repositories.NitriteProjPersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteProjectionSpec extends Specification implements NitriteTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteProjPersonRepository personRepository = applicationContext.getBean(NitriteProjPersonRepository)

    def cleanup() {
        personRepository.deleteAll()
    }

    def setup() {
        personRepository.saveAll([
                new NitriteProjPerson(firstName: "John", lastName: "Doe", age: 14, education: "No"),
                new NitriteProjPerson(firstName: "Joe", lastName: "Rabbit", age: 22, education: "Middle"),
                new NitriteProjPerson(firstName: "Frank", lastName: "Sink", age: 33, education: "High")
        ])
    }

    void 'test basic repository operations'() {
        when:
            def all = personRepository.findAll()
        then:
            all
            all.every {
                it.id
                it.firstName
                it.lastName
                it.age
                it.education
            }
        when:
            def byName = personRepository.findAllByFirstNameLike("J*")
        then:
            byName.size() == 2
    }
}
