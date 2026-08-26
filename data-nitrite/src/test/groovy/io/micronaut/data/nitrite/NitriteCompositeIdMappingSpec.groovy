package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.CompositeIdCollectionChild
import io.micronaut.data.nitrite.model.CompositeIdCollectionParent
import io.micronaut.data.nitrite.repository.CompositeIdCollectionChildRepository
import io.micronaut.data.nitrite.repository.CompositeIdCollectionParentRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.data.nitrite.model.CompositeIdEntity
import io.micronaut.data.nitrite.repository.CompositeIdEntityRepository
import io.micronaut.data.nitrite.model.MappedNumericCompositeIdEntity
import io.micronaut.data.nitrite.repository.MappedNumericCompositeIdEntityRepository

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
    CompositeIdCollectionParentRepository collectionParentRepository = context.getBean(CompositeIdCollectionParentRepository)

    @Shared
    CompositeIdCollectionChildRepository collectionChildRepository = context.getBean(CompositeIdCollectionChildRepository)

    @Shared
    CompositeIdEntityRepository parentRepository = context.getBean(CompositeIdEntityRepository)

    @Shared
    MappedNumericCompositeIdEntityRepository mappedNumericRepository = context.getBean(MappedNumericCompositeIdEntityRepository)

    def setup() {
        collectionChildRepository.deleteAll()
        collectionParentRepository.deleteAll()
        parentRepository.deleteAll()
        mappedNumericRepository.deleteAll()
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

    void "saving an existing composite identity does not insert a duplicate"() {
        given:
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "original"))

        when:
        parentRepository.save(new CompositeIdEntity(tenantId: "tenant-a", refId: "ref-1", name: "updated"))

        then:
        parentRepository.count() == 1
        parentRepository.findAll()[0].name == "updated"
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
}
