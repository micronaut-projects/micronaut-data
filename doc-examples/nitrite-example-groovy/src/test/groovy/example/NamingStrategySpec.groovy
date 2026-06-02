package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.LocalDate

@MicronautTest(transactional = false)
class NamingStrategySpec extends Specification {

    @Inject PersonWithDateOfBirthRepository personWithDateOfBirthRepository

    def cleanup() {
        personWithDateOfBirthRepository.deleteAll()
    }

    // tag::testSnakeCaseNaming[]
    def "snake_case naming strategy stores and retrieves fields correctly"() {
        given:
        personWithDateOfBirthRepository.saveAll([
            new PersonWithDateOfBirth("Alice", LocalDate.of(1990, 5, 15)),
            new PersonWithDateOfBirth("Bob", LocalDate.of(1985, 10, 20))
        ])

        when:
        def saved = personWithDateOfBirthRepository.findById(
            personWithDateOfBirthRepository.findAll().first().id).orElse(null)

        then:
        saved != null
        saved.name != null
        saved.dateOfBirth != null
    }
    // end::testSnakeCaseNaming[]

    def "count by name uses snake_case field"() {
        given:
        personWithDateOfBirthRepository.saveAll([
            new PersonWithDateOfBirth("Alice", LocalDate.of(1990, 5, 15)),
            new PersonWithDateOfBirth("Bob", LocalDate.of(1985, 10, 20))
        ])

        expect:
        personWithDateOfBirthRepository.countByName("Alice") == 1
    }
}
