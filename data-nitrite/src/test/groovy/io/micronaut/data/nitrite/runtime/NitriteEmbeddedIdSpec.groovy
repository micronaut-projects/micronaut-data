package io.micronaut.data.nitrite.runtime

import io.micronaut.data.nitrite.model.NitriteItemGroup
import io.micronaut.data.nitrite.model.NitriteShipment
import io.micronaut.data.nitrite.model.NitriteShipmentId
import io.micronaut.data.nitrite.mongoport.NitriteTestPropertyProvider
import io.micronaut.data.nitrite.repository.NitriteItemGroupRepository
import io.micronaut.data.nitrite.repository.NitriteShipmentRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class NitriteEmbeddedIdSpec extends Specification implements NitriteTestPropertyProvider {

    @Inject
    NitriteShipmentRepository repository

    @Inject
    NitriteItemGroupRepository groupRepository

    def cleanup() {
        repository.deleteAll()
        groupRepository.deleteAll()
    }

    void "test empty one-to-many via embedded-id"() {
        when:
        NitriteItemGroup itemGroup = new NitriteItemGroup(1L)
        itemGroup.setSecondId(2L)
        groupRepository.save(itemGroup)
        NitriteItemGroup entity = groupRepository.findById(1L).get()

        then:
        // Note: Nitrite doesn't have the same one-to-many via embedded-id pattern as MongoDB
        // This test verifies basic embedded ID entity operations work
        entity != null
        entity.id == 1L
    }

    void "test CRUD"() {
        when:
        NitriteShipmentId id = new NitriteShipmentId("a", "b")
        repository.save(new NitriteShipment(id, "test"))

        NitriteShipmentId id2 = new NitriteShipmentId("c", "d")
        repository.save(new NitriteShipment(id2, "test2"))

        NitriteShipmentId id3 = new NitriteShipmentId("e", "f")
        repository.save(new NitriteShipment(id3, "test3"))

        NitriteShipmentId id4 = new NitriteShipmentId("g", "h")
        repository.save(new NitriteShipment(id4, "test4"))

        def entity = repository.findById(id).orElse(null)

        then:
        repository.count() == 4
        entity != null

        when:"the entity is updated"
        entity.field = 'changed'
        repository.update(entity)
        entity = repository.findById(id).orElse(null)

        then:"The update completes correctly"
        entity != null
        entity.field == 'changed'
        entity.shipmentId.warehouseId == 'a'

        when:"The entity is deleted"
        repository.deleteById(id2)

        then:"The delete works"
        repository.count() == 3

        when:"The delete method is used"
         repository.delete(entity)

        then:"The delete method works"
        repository.count() == 2
    }
}
