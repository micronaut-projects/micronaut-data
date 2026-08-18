package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.CompositeIdChild
import io.micronaut.data.nitrite.model.CompositeIdEntity
import io.micronaut.data.nitrite.model.CompositePageEntity
import io.micronaut.data.nitrite.repository.CompositeIdChildRepository
import io.micronaut.data.nitrite.repository.CompositeIdEntityRepository
import io.micronaut.data.nitrite.repository.CompositePageEntityRepository
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

    def setup() {
        repository.deleteAll()
        childRepository.deleteAll()
        parentRepository.deleteAll()
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
}
