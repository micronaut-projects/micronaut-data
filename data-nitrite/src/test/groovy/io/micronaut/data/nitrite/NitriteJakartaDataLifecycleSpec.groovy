package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.nitrite.model.ImmutableVersionedPerson
import io.micronaut.data.nitrite.model.StringIdEntity
import io.micronaut.data.nitrite.repository.ImmutableVersionedPersonRepository
import io.micronaut.data.nitrite.repository.StringIdRepository
import org.dizitart.no2.Nitrite
import org.dizitart.no2.filters.Filter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Conformance to the Jakarta Data lifecycle contract, which is what the provider's strict CRUD
 * semantics are taken from. Each case names the clause it covers.
 */
class NitriteJakartaDataLifecycleSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
    ])

    @Shared
    StringIdRepository repository = context.getBean(StringIdRepository)

    @Shared
    ImmutableVersionedPersonRepository immutableRepository = context.getBean(ImmutableVersionedPersonRepository)

    void setup() {
        repository.deleteAll()
        // An immutable entity cannot be read back (see nitriteLimitations.adoc), so its documents
        // are cleared through the collection rather than through the repository.
        context.getBean(Nitrite).getCollection("ImmutableVersionedPerson").remove(Filter.ALL)
    }

    // @Insert: "raises EntityExistsException ... if an entity of this type with the same unique
    // identifier already exists". The identity here is a String, so the rejection comes from the
    // unique index on the identity field rather than from Nitrite's own document key.
    void "insert of an identity that already exists is rejected"() {
        given:
            repository.save(new StringIdEntity("dup-1", "first"))

        when:
            repository.insert(new StringIdEntity("dup-1", "second"))

        then:
            thrown(EntityExistsException)
    }

    // @Update: "If no entity with a matching identifier is found in the database ... must raise
    // OptimisticLockingFailureException". The clause is not conditioned on the entity carrying a
    // version, and StringIdEntity carries none.
    void "update of an unversioned entity that is absent is rejected"() {
        when:
            repository.update(new StringIdEntity("absent-1", "nothing here"))

        then:
            thrown(OptimisticLockException)
    }

    // BasicRepository.delete: raises "if the entity is not found in the database for deletion".
    // Again unconditional on the entity being versioned.
    void "delete of an unversioned entity that is absent is rejected"() {
        when:
            repository.delete(new StringIdEntity("absent-2", "nothing here"))

        then:
            thrown(OptimisticLockException)
    }

    // @Save: the operation "depends on whether the database already holds an entity with the
    // unique identifier", not on whether that identifier is null. An assigned identity is
    // therefore inserted on its first save and updated on the next.
    void "save of an assigned identity inserts first and updates after"() {
        when: "an identity the store does not hold"
            repository.save(new StringIdEntity("assigned-1", "first"))

        then: "it is inserted rather than rejected as absent"
            repository.findById("assigned-1").get().name == "first"
            repository.count() == 1

        when: "the same identity is saved again"
            repository.save(new StringIdEntity("assigned-1", "second"))

        then: "it is updated rather than duplicated"
            repository.findById("assigned-1").get().name == "second"
            repository.count() == 1
    }

    // @Save over a batch resolves each entity independently, so a mixed batch is part insert and
    // part update.
    void "saveAll resolves each entity of a mixed batch independently"() {
        given:
            repository.save(new StringIdEntity("mixed-1", "original"))

        when:
            repository.saveAll([
                    new StringIdEntity("mixed-1", "updated"),
                    new StringIdEntity("mixed-2", "inserted")])

        then:
            repository.count() == 2
            repository.findById("mixed-1").get().name == "updated"
            repository.findById("mixed-2").get().name == "inserted"
    }

    // @Insert: the returned instance "must include all values that were written to the database,
    // including all automatically generated values and incremented values". An immutable entity
    // cannot be handed its initial version in place, so what comes back has to be the replacement
    // instance the provider built, not the instance the caller passed in.
    void "saveAll returns immutable entities carrying their initial version"() {
        when:
            def saved = immutableRepository.saveAll([
                    new ImmutableVersionedPerson("imm-1", "Amy"),
                    new ImmutableVersionedPerson("imm-2", "Ben")]).toList()

        then:
            saved.size() == 2
            saved*.version == [0L, 0L]
            saved*.name == ["Amy", "Ben"]
    }

    // The same clause over a single save.
    void "save returns an immutable entity carrying its initial version"() {
        when:
            def saved = immutableRepository.save(new ImmutableVersionedPerson("imm-3", "Cal"))

        then:
            saved.version == 0L
    }
}
