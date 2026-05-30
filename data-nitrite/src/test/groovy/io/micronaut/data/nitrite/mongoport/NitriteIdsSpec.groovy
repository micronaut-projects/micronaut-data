package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.mongoport.entities.NitriteMpPerson
import io.micronaut.data.nitrite.mongoport.repositories.NitriteMpPersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteIdsSpec extends Specification implements NitriteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteMpPersonRepository personRepository = applicationContext.getBean(NitriteMpPersonRepository)

    def cleanup() {
        personRepository.deleteAll()
    }

    void 'test generated id is not null'() {
        when:
            NitriteMpPerson person = new NitriteMpPerson(name: "Test")
            personRepository.save(person)

        then:
            person.id != null
    }

    void 'test find by id'() {
        when:
            NitriteMpPerson person = new NitriteMpPerson(name: "FindByMe")
            personRepository.save(person)
            NitriteMpPerson found = personRepository.findById(person.id).get()

        then:
            found.name == "FindByMe"
    }

    void 'test exists by id'() {
        when:
            NitriteMpPerson person = new NitriteMpPerson(name: "ExistsTest")
            personRepository.save(person)
            boolean exists = personRepository.existsById(person.id)

        then:
            exists

        when:
            personRepository.deleteById(person.id)
            exists = personRepository.existsById(person.id)

        then:
            !exists
    }

    void 'test delete by id'() {
        when:
            NitriteMpPerson person = new NitriteMpPerson(name: "DeleteTest")
            personRepository.save(person)
            personRepository.deleteById(person.id)
            def result = personRepository.findById(person.id)

        then:
            !result.isPresent()
    }

    void 'test count'() {
        when:
            personRepository.save(new NitriteMpPerson(name: "One"))
            personRepository.save(new NitriteMpPerson(name: "Two"))
            personRepository.save(new NitriteMpPerson(name: "Three"))

        then:
            personRepository.count() == 3
    }
}
