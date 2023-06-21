/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.h2

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.Shipment
import io.micronaut.data.tck.entities.ShipmentId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.persistence.Entity
import jakarta.validation.constraints.NotNull

@MicronautTest
@H2DBProperties
class H2EmbeddedIdSpec extends Specification {

    @Inject
    ShipmentRepository repository

    @Inject
    ItemGroupRepository groupRepository

    void "test empty one-to-many via embedded-id"() {
        when:
        ItemGroup itemGroup = new ItemGroup(1L)
        itemGroup.setSecondId(2L)
        groupRepository.save(itemGroup)
        ItemGroup entity = groupRepository.findById(1L).get()

        then:
        entity.getItems().size() == 0
    }

    void "test CRUD"() {
        when:
        ShipmentId id = new ShipmentId("a", "b")
        repository.save(new Shipment(id, "test"))

        ShipmentId id2 = new ShipmentId("c", "d")
        repository.save(new Shipment(id2, "test2"))

        ShipmentId id3 = new ShipmentId("e", "f")
        repository.save(new Shipment(id3, "test3"))

        ShipmentId id4 = new ShipmentId("g", "h")
        repository.save(new Shipment(id4, "test4"))

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
        entity.shipmentId.city == 'b'

        when:"The entity is deleted"
        repository.deleteById(id2)

        then:"The delete works"
        repository.count() == 3

        when:"The delete method is used"
         repository.delete(entity)

        then:"The delete method works"
        repository.count() == 2

        when:"Find all is used"
        def all = repository.findAll()

        then:"all is correct"
        all.size() == 2

        when:"Find by country"
        def foundByCountry = repository.findByShipmentIdCountry("g")

        then:
        foundByCountry.field == "test4"
        foundByCountry.shipmentId.country == "g"
        foundByCountry.shipmentId.city == "h"

        when:"Find by country and city"
        def foundByCountryAndCIty = repository.findByShipmentIdCountryAndShipmentIdCity("g", "h")

        then:
        foundByCountryAndCIty.field == "test4"
        foundByCountryAndCIty.shipmentId.country == "g"
        foundByCountryAndCIty.shipmentId.city == "h"

        when:"Find all order by association path"
        def foundAllOrderByCityDesc = repository.findAllOrderByShipmentIdCityDesc()
        def foundAllOrderByCountryCityDesc = repository.findAllOrderByShipmentIdCountryAndShipmentIdCityDesc()

        then:
        foundAllOrderByCityDesc.size() == 2
        foundAllOrderByCityDesc[0].field == "test4"
        foundAllOrderByCityDesc[0].shipmentId.country == id4.country
        foundAllOrderByCityDesc[0].shipmentId.city == id4.city
        foundAllOrderByCityDesc[1].field == "test3"
        foundAllOrderByCityDesc[1].shipmentId.country == id3.country
        foundAllOrderByCityDesc[1].shipmentId.city == id3.city
        foundAllOrderByCountryCityDesc.size() == 2
        foundAllOrderByCountryCityDesc[0].field == "test3"
        foundAllOrderByCountryCityDesc[1].field == "test4"

        when:"deleteAll is used with an iterable"
        repository.deleteAll([all.first()])

        then:"The entities where deleted"
        repository.count() == 1

        when:"deleteAll is used"
        repository.deleteAll()

        then:"The entities where deleted"
        repository.count() == 0
    }
}

@Entity
class ItemGroup {

    @Id
    private Long id;

    private Long secondId;

    ItemGroup(Long id) {
        this.id = id
    }

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private Set<Item> items = new HashSet<>();

    Long getId() {
        return id
    }

    void setId(Long id) {
        this.id = id
    }

    Long getSecondId() {
        return secondId
    }

    void setSecondId(Long secondId) {
        this.secondId = secondId
    }

    Set<Item> getItems() {
        return items;
    }

    void setItems(Set<Item> shipments) {
        this.items = shipments;
    }
}

@Entity
class Item {

    @EmbeddedId
    private ItemGroupId id;

    ItemGroupId getId() {
        return id
    }

    void setId(ItemGroupId id) {
        this.id = id
    }
}

@Introspected
@Embeddable
public class ItemGroupId {

    ItemGroupId(Long firstId, Long secondId) {
        this.firstId = firstId
        this.secondId = secondId
    }
    private Long firstId

    private Long secondId

    Long getFirstId() {
        return firstId
    }

    void setFirstId(Long firstId) {
        this.firstId = firstId
    }

    Long getSecondId() {
        return secondId
    }

    void setSecondId(Long secondId) {
        this.secondId = secondId
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface ItemGroupRepository extends CrudRepository<ItemGroup, Long> {

    @Override
    @Join(value = "items", type = Join.Type.LEFT_FETCH)
    public abstract Optional<ItemGroup> findById(@NotNull Long id);
}
