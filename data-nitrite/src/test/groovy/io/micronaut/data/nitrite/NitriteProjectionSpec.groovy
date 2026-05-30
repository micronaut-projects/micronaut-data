package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.CriteriaPerson
import io.micronaut.data.nitrite.repository.ProjectionPersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteProjectionSpec extends Specification {

    @Inject
    ProjectionPersonRepository repository

    def setup() {
        repository.deleteAll()
        repository.saveAll([
            new CriteriaPerson("Alice", 25),
            new CriteriaPerson("Bob", 30),
            new CriteriaPerson("Charlie", 35)
        ])
    }

    void "test single property projection - findOne"() {
        expect:
        repository.findAgeByName("Alice").get() == 25
        repository.findAgeByName("Bob").get() == 30
        repository.findAgeByName("Unknown").isEmpty()
    }

    void "test single property projection - findAll"() {
        when:
        def names = repository.findNameByAgeGreaterThan(28)

        then:
        names.size() == 2
        names.contains("Bob")
        names.contains("Charlie")
    }

    void "test single property projection with LIKE"() {
        when:
        def ages = repository.listAgeByNameLike("Alic%")

        then:
        ages.size() == 1
        ages[0] == 25
    }
}
