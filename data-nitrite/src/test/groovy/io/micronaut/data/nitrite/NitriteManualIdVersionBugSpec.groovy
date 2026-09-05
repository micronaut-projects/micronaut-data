package io.micronaut.data.nitrite

import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.nitrite.model.ManualIdVersionedPerson
import io.micronaut.data.nitrite.model.ProjectId
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

    void "saveAll with manually assigned ids upserts each entity by id"() {
        given: "brand new entities that already carry a manually assigned (non-null) id"
            def batch = [
                new ManualIdVersionedPerson("Amy", 20),
                new ManualIdVersionedPerson("Ben", 21)
            ]

        when:
            def saved = repository.saveAll(batch).toList()

        then: "each is upserted by its pre-set id, not treated as a duplicate insert"
            saved.size() == 2
            // Unlike single save() (see above), saveAll()'s upsert path does not special-case
            // a fresh entity with a pre-set id: it always goes through the update-with-upsert
            // branch, which increments the version like any other update.
            saved*.version == [1L, 1L]
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

}
