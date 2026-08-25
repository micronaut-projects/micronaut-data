package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.nitrite.model.CompositeIdChild
import io.micronaut.data.nitrite.model.CompositeIdCollectionChild
import io.micronaut.data.nitrite.model.CompositeIdCollectionParent
import io.micronaut.data.nitrite.model.CompositeIdEntity
import io.micronaut.data.nitrite.model.CompositePageEntity
import io.micronaut.data.nitrite.model.MappedNumericCompositeIdEntity
import io.micronaut.data.nitrite.model.VersionedCompositeIdEntity
import io.micronaut.data.nitrite.repository.CompositeIdChildRepository
import io.micronaut.data.nitrite.repository.CompositeIdCollectionChildRepository
import io.micronaut.data.nitrite.repository.CompositeIdCollectionParentRepository
import io.micronaut.data.nitrite.repository.CompositeIdEntityRepository
import io.micronaut.data.nitrite.repository.CompositePageEntityRepository
import io.micronaut.data.nitrite.repository.MappedNumericCompositeIdEntityRepository
import io.micronaut.data.nitrite.repository.VersionedCompositeIdEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * An entity with several {@code @Id} properties has to store those properties like any other, so
 * they survive a round trip and can be filtered and sorted on, and so the composite identity can
 * act as a pagination tie-breaker (see NitriteCompositeIdPaginationSpec). Identity properties are
 * not part of getPersistentProperties(), so the mapper writes and reads them explicitly.
 */
class NitriteCompositeIdMappingSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])

    @Shared
    CompositePageEntityRepository repository = context.getBean(CompositePageEntityRepository)

    @Shared
    CompositeIdEntityRepository parentRepository = context.getBean(CompositeIdEntityRepository)

    @Shared
    CompositeIdChildRepository childRepository = context.getBean(CompositeIdChildRepository)

    @Shared
    CompositeIdCollectionParentRepository collectionParentRepository = context.getBean(CompositeIdCollectionParentRepository)

    @Shared
    CompositeIdCollectionChildRepository collectionChildRepository = context.getBean(CompositeIdCollectionChildRepository)

    @Shared
    VersionedCompositeIdEntityRepository versionedRepository = context.getBean(VersionedCompositeIdEntityRepository)

    @Shared
    MappedNumericCompositeIdEntityRepository mappedNumericRepository = context.getBean(MappedNumericCompositeIdEntityRepository)

    def setup() {
        repository.deleteAll()
        childRepository.deleteAll()
        parentRepository.deleteAll()
        collectionChildRepository.deleteAll()
        collectionParentRepository.deleteAll()
        versionedRepository.deleteAll()
        mappedNumericRepository.deleteAll()
    }

    void "the properties of a composite identity survive a round trip"() {
        given:
        repository.save(new CompositePageEntity("shard-a", "seq-1", "key", "payload"))

        when:
        def loaded = repository.findAll()

        then:
        loaded.size() == 1
        loaded[0].shard == "shard-a"
        loaded[0].seq == "seq-1"
    }

    void "saving an existing composite identity does not insert a duplicate"() {
        given:
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "original"))

        when:
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "updated"))

        then:
        parentRepository.count() == 1
        parentRepository.findAll()[0].name == "updated"
    }

    void "saveAll upserts an existing composite identity"() {
        given:
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "original"))

        when:
        parentRepository.saveAll([
            new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "updated")
        ])

        then:
        parentRepository.count() == 1
        parentRepository.findAll()[0].name == "updated"
    }

    void "update replaces an existing composite identity"() {
        given:
        def entity = parentRepository.save(new CompositeIdEntity(
            tenantId: "tenant-a", refId: "ref-1", name: "original"))
        entity.name = "updated"

        when:
        parentRepository.update(entity)

        then:
        parentRepository.count() == 1
        parentRepository.findAll()[0].name == "updated"
    }

    void "updateAll replaces existing composite identities"() {
        given:
        def first = parentRepository.save(new CompositeIdEntity(
            tenantId: "tenant-a", refId: "ref-1", name: "first"))
        def second = parentRepository.save(new CompositeIdEntity(
            tenantId: "tenant-a", refId: "ref-2", name: "second"))
        first.name = "first-updated"
        second.name = "second-updated"

        when:
        parentRepository.updateAll([first, second])

        then:
        parentRepository.findAll()*.name.toSet() == ["first-updated", "second-updated"].toSet()
    }

    void "delete removes an entity with a composite identity"() {
        given:
        def entity = parentRepository.save(new CompositeIdEntity(
            tenantId: "tenant-a", refId: "ref-1", name: "to-delete"))

        when:
        parentRepository.delete(entity)

        then:
        parentRepository.count() == 0
    }

    void "deleteAll with an iterable removes only the selected composite identities"() {
        given:
        def first = parentRepository.save(new CompositeIdEntity(
            tenantId: "tenant-a", refId: "ref-1", name: "first"))
        def second = parentRepository.save(new CompositeIdEntity(
            tenantId: "tenant-a", refId: "ref-2", name: "second"))

        when:
        parentRepository.deleteAll([first])

        then:
        parentRepository.count() == 1
        parentRepository.findAll()[0].refId == second.refId
    }

    void "strict insert rejects a duplicate composite identity"() {
        given:
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "original"))

        when:
        parentRepository.insertOne(new CompositeIdEntity(
            tenantId: "tenant-a", refId: "ref-1", name: "duplicate"))

        then:
        thrown(EntityExistsException)
    }

    void "findById and deleteById use both composite identity properties"() {
        given:
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "target"))
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-2", name: "other"))
        def key = new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1")

        expect:
        parentRepository.findById(key).orElseThrow().name == "target"

        when:
        parentRepository.deleteById(key)

        then:
        parentRepository.count() == 1
        parentRepository.findAll()[0].refId == "ref-2"
    }

    void "a partial composite identity matches nothing and deletes nothing"() {
        given: "only one half of the identity is set"
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "target"))
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-2", name: "other"))
        def partial = new CompositeIdEntity(tenantId: "tenant-a")

        expect: "the set half does not stand in for the whole identity"
        parentRepository.findById(partial).isEmpty()

        when:
        parentRepository.deleteById(partial)

        then: "neither row sharing the set half is removed"
        parentRepository.count() == 2
    }

    void "a derived query matches the complete composite identity"() {
        given:
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "target"))
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-2", name: "other"))

        expect:
        parentRepository.findByTenantIdAndRefId("tenant-a", "ref-1").orElseThrow().name == "target"
    }

    void "a versioned composite identity is updated by save"() {
        given:
        def entity = versionedRepository.save(new VersionedCompositeIdEntity("tenant-a", "ref-1", "original"))
        entity.name = "updated"

        when:
        def updated = versionedRepository.save(entity)

        then:
        updated.version == 1L
        versionedRepository.count() == 1
        versionedRepository.findAll()[0].name == "updated"
        versionedRepository.findAll()[0].version == 1L
    }

    void "a versioned composite identity is updated by update"() {
        given:
        def entity = versionedRepository.save(new VersionedCompositeIdEntity("tenant-a", "ref-1", "original"))
        entity.name = "updated"

        when:
        versionedRepository.update(entity)

        then:
        entity.version == 1L
        versionedRepository.findAll()[0].name == "updated"
        versionedRepository.findAll()[0].version == 1L
    }

    void "a mapped numeric composite identity survives a round trip and exact lookup"() {
        given:
        mappedNumericRepository.save(new MappedNumericCompositeIdEntity(42L, 7, "target"))

        when:
        def loaded = mappedNumericRepository.findAll()[0]

        then:
        loaded.tenantId == 42L
        loaded.sequence == 7
        mappedNumericRepository.findByTenantIdAndSequence(42L, 7).orElseThrow().name == "target"
    }

    void "an association to a composite-identity entity hydrates through its mapped join columns"() {
        given: "a parent with no single id property at all, so the association resolves no associated id"
        def parent = parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "parent"))
        childRepository.save(new CompositeIdChild("child-a", parent))

        when:
        def child = childRepository.findByName("child-a").orElseThrow()

        then: "the identity arrives from the @JoinColumn mapping, not from a single-id reference"
        child.parent != null
        child.parent.tenantId == "tenant-a"
        child.parent.refId == "ref-1"
    }

    void "a joined collection stays empty when the parent identity is only half populated"() {
        given: "a parent missing one half of its identity, and children sharing the half it has"
        collectionParentRepository.save(new CompositeIdCollectionParent("tenant-b", null, "incomplete"))
        collectionChildRepository.save(new CompositeIdCollectionChild("child-x", "tenant-b", null))

        when:
        def parent = collectionParentRepository.findByName("incomplete").orElseThrow()

        then: "half an identity matches no children rather than every child sharing that half"
        !parent.children
    }

    void "a joined collection can hydrate children whose parent has a composite identity"() {
        given:
        collectionParentRepository.save(new CompositeIdCollectionParent("tenant-a", "ref-1", "parent"))
        collectionChildRepository.save(new CompositeIdCollectionChild("child-a", "tenant-a", "ref-1"))
        collectionChildRepository.save(new CompositeIdCollectionChild("child-b", "tenant-a", "ref-1"))

        when:
        def parent = collectionParentRepository.findByTenantIdAndRefId("tenant-a", "ref-1").orElseThrow()

        then:
        parent.children*.name.toSet() == ["child-a", "child-b"].toSet()
    }
}
