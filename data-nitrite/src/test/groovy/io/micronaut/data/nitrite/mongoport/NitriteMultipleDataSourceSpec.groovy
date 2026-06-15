package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.NitriteMpPerson
import io.micronaut.data.nitrite.repository.NitriteMpPersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Test for Nitrite storage operations.
 * This spec tests basic storage functionality that would be used in multi-datasource scenarios.
 */
@MicronautTest
class NitriteMultipleDataSourceSpec extends Specification implements NitriteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteMpPersonRepository personRepository = applicationContext.getBean(NitriteMpPersonRepository)

    def cleanup() {
        personRepository.deleteAll()
    }

    void 'test basic storage operations'() {
        when:
            personRepository.save(new NitriteMpPerson(name: "Test User"))
            def people = personRepository.findAll().toList()

        then:
            people.size() == 1
            people[0].name == "Test User"
    }

    void 'test storage isolation'() {
        when:
            personRepository.save(new NitriteMpPerson(name: "User 1"))
            personRepository.save(new NitriteMpPerson(name: "User 2"))
            def count = personRepository.count()

        then:
            count == 2

        when:
            personRepository.deleteAll()
            count = personRepository.count()

        then:
            count == 0
    }
}
