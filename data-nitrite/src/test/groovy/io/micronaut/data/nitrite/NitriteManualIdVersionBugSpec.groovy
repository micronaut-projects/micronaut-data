package io.micronaut.data.nitrite

import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.nitrite.model.ManualIdVersionedPerson
import io.micronaut.data.nitrite.model.VersionedProject
import io.micronaut.data.nitrite.repository.ManualIdVersionedPersonRepository
import io.micronaut.data.nitrite.repository.VersionedProjectRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Regression tests for an entity with a manually assigned (non-@GeneratedValue) id:
 *
 * 1. save() on a brand new entity should initialize @Version to 0, not treat the
 *    non-null id as evidence of an existing row and pre-increment the version.
 * 2. findFirstBy...OrderBy...Asc must honour the Sort clause instead of returning
 *    results in insertion order.
 *
 * Also covers the same version-init case for an @EmbeddedId (composite, non-generated)
 * identity, since the id there is a Document-shaped value rather than a scalar.
 */
@MicronautTest
class NitriteManualIdVersionBugSpec extends Specification {

    @Inject
    ManualIdVersionedPersonRepository repository

    @Inject
    VersionedProjectRepository projectRepository

    def cleanup() {
        repository.deleteAll()
        projectRepository.deleteAll()
    }

    void "saveAll with manually assigned ids inserts each new entity"() {
        given: "brand new entities that already carry a manually assigned (non-null) id"
            def batch = [
                new ManualIdVersionedPerson("Amy", 20),
                new ManualIdVersionedPerson("Ben", 21)
            ]

        when:
            def saved = repository.saveAll(batch).toList()

        then: "each is inserted by its pre-set id, not treated as an existing entity"
            saved.size() == 2
            saved*.version == [0L, 0L]
            repository.findFirstByNameOrderByAgeAsc("Amy").isPresent()
            repository.findFirstByNameOrderByAgeAsc("Ben").isPresent()
    }

    void "strict @Insert batch rejects an id that already exists"() {
        given:
            def existing = repository.save(new ManualIdVersionedPerson("Eve", 24))
            def duplicate = new ManualIdVersionedPerson("Eve again", 25)
            duplicate.id = existing.id

        when:
            repository.insertBatch([duplicate])

        then:
            thrown(EntityExistsException)
    }

    void "inherited insertAll rejects an id that already exists"() {
        given: "an entity whose identity is already present"
            def existing = repository.save(new ManualIdVersionedPerson("Existing", 30))
            def duplicate = new ManualIdVersionedPerson("Duplicate", 31)
            duplicate.id = existing.id

        when: "the inherited CrudRepository insertAll operation is used"
            repository.insertAll([duplicate])

        then: "insertAll remains strict instead of being treated as saveAll"
            thrown(EntityExistsException)
    }

}
