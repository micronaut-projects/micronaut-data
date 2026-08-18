package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.nitrite.model.ManualIdVersionedPerson
import io.micronaut.data.nitrite.model.Person
import io.micronaut.data.nitrite.repository.ManualIdVersionedPersonRepository
import io.micronaut.data.nitrite.repository.PersonRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Update semantics for an entity the store has never seen. Without a {@code @Version} property an
 * update writes the document, because an application-assigned id is the only evidence of intent
 * Nitrite has; with one, the version takes part in the filter and the update stays strict. Both are
 * documented under "Remaining Limitations" in the Nitrite guide.
 */
class NitriteUpdateUpsertSemanticsSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])

    @Shared
    PersonRepository repository = context.getBean(PersonRepository)

    @Shared
    ManualIdVersionedPersonRepository versionedRepository = context.getBean(ManualIdVersionedPersonRepository)

    def setup() {
        repository.deleteAll()
        versionedRepository.deleteAll()
    }

    void "update of an unversioned entity with an unused id stores it"() {
        given: "an unversioned entity carrying an id that no document uses"
        def ghost = new Person("Ghost", 30)
        ghost.id = "missing-id"

        when:
        repository.update(ghost)

        then: "the update runs with upsert, so the entity is stored under the id it carries"
        repository.count() == 1
        repository.findById("missing-id").get().name == "Ghost"
    }

    void "update of an unversioned entity without an id stores nothing"() {
        given:
        def transientPerson = new Person("Nameless", 30)

        when:
        repository.update(transientPerson)

        then: "an id-less filter would match every identity-less document, so the update is refused"
        repository.count() == 0
    }

    void "save assigns an id where update requires one"() {
        when: "the same entity is saved instead of updated"
        def saved = repository.save(new Person("Named", 30))

        then: "save is the operation that makes a new entity, generated id included"
        saved.id != null
        repository.count() == 1
    }

    void "update of a versioned entity with an unused id inserts nothing"() {
        given: "a versioned entity that was never stored"
        def ghost = new ManualIdVersionedPerson("Ghost", 30)
        ghost.version = 1L

        when:
        versionedRepository.update(ghost)

        then: "the version is part of the filter, so nothing matches and nothing is written"
        thrown(OptimisticLockException)
        versionedRepository.count() == 0
    }
}
