package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.mongoport.entities.NitriteMpPerson
import io.micronaut.data.nitrite.mongoport.repositories.NitriteMpPersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Test for custom Nitrite storage configuration.
 * Tests different storage modes available in Nitrite.
 */
@MicronautTest
class NitriteCustomStorageSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    @Inject
    NitriteMpPersonRepository personRepository

    def setup() {
        // Use in-memory storage for testing
        applicationContext = ApplicationContext.run([
                'micronaut.data.nitrite.storage': 'memory',
                'micronaut.data.nitrite.create-indexes': 'true'
        ])
        personRepository = applicationContext.getBean(NitriteMpPersonRepository)
    }

    def cleanup() {
        personRepository?.deleteAll()
        applicationContext?.close()
    }

    void 'test in-memory storage'() {
        when:
            personRepository.save(new NitriteMpPerson(name: "Test User"))
            def people = personRepository.findAll().toList()

        then:
            people.size() == 1
            people[0].name == "Test User"
    }

    void 'test storage persistence within session'() {
        when:
            personRepository.save(new NitriteMpPerson(name: "Persistent User"))
            def count = personRepository.count()

        then:
            count == 1

        when:
            def people = personRepository.customFind("Persistent.*")

        then:
            people.size() == 1
    }
}
