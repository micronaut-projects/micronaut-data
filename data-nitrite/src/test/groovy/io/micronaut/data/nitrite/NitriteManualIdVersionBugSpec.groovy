package io.micronaut.data.nitrite

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
