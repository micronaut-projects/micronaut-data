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

    void "new entity is saved with version 0, not 1"() {
        when: "saving a brand new entity that already carries a manually assigned id"
            ManualIdVersionedPerson saved = repository.save(new ManualIdVersionedPerson("John", 30))

        then: "version should be initialized, not pre-incremented as if this were an update"
            saved.version == 0L
    }

    void "findFirstByNameOrderByAgeAsc honours the Sort clause"() {
        given:
            repository.save(new ManualIdVersionedPerson("John", 35))
            repository.save(new ManualIdVersionedPerson("John", 25))
            repository.save(new ManualIdVersionedPerson("John", 30))

        when:
            Optional<ManualIdVersionedPerson> found = repository.findFirstByNameOrderByAgeAsc("John")

        then: "the lowest age should win, not insertion order"
            found.isPresent()
            found.get().age == 25
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

    void "strict @Insert batch inserts entities with pre-set ids"() {
        given:
            def batch = [
                new ManualIdVersionedPerson("Cara", 22),
                new ManualIdVersionedPerson("Dan", 23)
            ]

        when:
            def saved = repository.insertBatch(batch)

        then:
            saved.size() == 2
            repository.findFirstByNameOrderByAgeAsc("Cara").isPresent()
            repository.findFirstByNameOrderByAgeAsc("Dan").isPresent()
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

    void "strict @Insert single-entity inserts an entity with a pre-set id"() {
        given:
            def person = new ManualIdVersionedPerson("Frank", 26)

        when:
            def saved = repository.insertOne(person)

        then:
            repository.findFirstByNameOrderByAgeAsc("Frank").isPresent()
            saved.id == person.id
    }

    void "strict @Insert single-entity rejects an id that already exists"() {
        given:
            def existing = repository.save(new ManualIdVersionedPerson("Grace", 27))
            def duplicate = new ManualIdVersionedPerson("Grace again", 28)
            duplicate.id = existing.id

        when:
            repository.insertOne(duplicate)

        then:
            thrown(EntityExistsException)
    }

    void "new entity with a composite @EmbeddedId is saved with version 0, not 1"() {
        when: "saving a brand new entity that already carries a manually assigned composite id"
            VersionedProject saved = projectRepository.save(
                new VersionedProject(new ProjectId(1, 100), "Alpha"))

        then: "version should be initialized, not pre-incremented as if this were an update"
            saved.version == 0L
    }

    void "re-saving an existing @EmbeddedId entity increments the version"() {
        given:
            VersionedProject saved = projectRepository.save(
                new VersionedProject(new ProjectId(2, 200), "Beta"))

        when:
            saved.name = "Beta v2"
            VersionedProject updated = projectRepository.save(saved)

        then: "the existence check must recognise the row despite the composite id"
            updated.version == 1L
    }
}
