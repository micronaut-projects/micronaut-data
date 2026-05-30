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
class NitriteDocumentTransactionSpec extends Specification implements NitriteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteMpPersonRepository personRepository = applicationContext.getBean(NitriteMpPersonRepository)

    def cleanup() {
        personRepository.deleteAll()
    }

    void 'test basic CRUD operations'() {
        when:
            NitriteMpPerson person = new NitriteMpPerson(name: "Alice")
            personRepository.save(person)
            def people = personRepository.findAll().toList()
        then:
            people.size() == 1
            people[0].name == "Alice"

        when:
            person.name = "Alice Updated"
            personRepository.update(person)
            NitriteMpPerson updated = personRepository.findById(person.id).get()
        then:
            updated.name == "Alice Updated"

        when:
            personRepository.deleteById(person.id)
            people = personRepository.findAll().toList()
        then:
            people.isEmpty()
    }

    void 'test save all'() {
        when:
            personRepository.saveAll([
                new NitriteMpPerson(name: "Bob"),
                new NitriteMpPerson(name: "Charlie")
            ])
            def people = personRepository.findAll().toList()
        then:
            people.size() == 2
            people.collect { it.name }.sort() == ["Bob", "Charlie"]
    }
}
