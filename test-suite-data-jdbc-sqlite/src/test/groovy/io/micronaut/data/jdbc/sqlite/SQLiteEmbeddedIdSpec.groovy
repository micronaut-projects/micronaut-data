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
package io.micronaut.data.jdbc.sqlite

import io.micronaut.core.annotation.Introspected
import org.jspecify.annotations.NonNull
import org.jspecify.annotations.Nullable
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.PageableRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.tck.entities.Shipment
import io.micronaut.data.tck.entities.ShipmentId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.persistence.Entity
import jakarta.validation.constraints.NotNull

@MicronautTest
@SQLiteDBProperties
class SQLiteEmbeddedIdSpec extends Specification {

    @Inject
    ShipmentRepository repository

    @Inject
    ItemGroupRepository groupRepository

    @Inject
    ConfigurationItemRepository configurationItemRepository

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
        given:
        repository.deleteAll()

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

        when:
        def foundAllOrderByDynamic = repository.findAll(Sort.of(Sort.Order.desc("shipmentId.country"), Sort.Order.asc( "shipmentId.city")))

        then:
        foundAllOrderByDynamic.size() == 2
        foundAllOrderByDynamic[0].shipmentId.country == "g"
        foundAllOrderByDynamic[0].shipmentId.city == "h"
        foundAllOrderByDynamic[1].shipmentId.country == "e"
        foundAllOrderByDynamic[1].shipmentId.city == "f"

        then:
        foundAllOrderByDynamic.size() == 2

        when:"deleteAll is used with an iterable"
        repository.deleteAll([all.first()])

        then:"The entities where deleted"
        repository.count() == 1

        when:"deleteAll is used"
        repository.deleteAll()

        then:"The entities where deleted"
        repository.count() == 0
    }

    void "test criteria order of embedded"() {
        given:
        repository.deleteAll()
        when:
        ShipmentId id = new ShipmentId("a", "b")
        repository.save(new Shipment(id, "test"))

        ShipmentId id2 = new ShipmentId("c", "d")
        repository.save(new Shipment(id2, "test2"))

        ShipmentId id3 = new ShipmentId("e", "f")
        repository.save(new Shipment(id3, "test3"))

        ShipmentId id4 = new ShipmentId("g", "h")
        repository.save(new Shipment(id4, "test4"))

        Sort.Order.Direction sortDirection = Sort.Order.Direction.ASC;
        Pageable pageable = Pageable.UNPAGED.order(new Sort.Order("shipmentId.city", sortDirection, false));
        def page = repository.findAll(pageable)

        then:
        page.totalSize == 4
        page.content[0].shipmentId.city == "b"

        cleanup:
        repository.deleteAll()
    }

    void "test cursored pageable"() {
        when:
        ShipmentId id = new ShipmentId("c1", "a")
        repository.save(new Shipment(id, "test"))

        ShipmentId id2 = new ShipmentId("c1", "b")
        repository.save(new Shipment(id2, "test2"))

        ShipmentId id3 = new ShipmentId("c1", "c")
        repository.save(new Shipment(id3, "test3"))

        ShipmentId id4 = new ShipmentId("c1", "d")
        repository.save(new Shipment(id4, "test4"))

        ShipmentId id5 = new ShipmentId("c2", "a1")
        repository.save(new Shipment(id5, "test5"))

        CursoredPageable cursoredPageable = CursoredPageable.from(3, Sort.of());
        CursoredPage<Shipment> page = repository.findByShipmentIdCountry("c1", cursoredPageable)

        then:
        page.content.size() == 3
        page.hasNext()

        when:
        page = repository.findByShipmentIdCountry("c1", page.nextPageable())

        then:
        page.content.size() == 1
        !page.hasNext()

        cleanup:
        repository.deleteAll()
    }

    void "test pagination"() {
        when:
        def id = new ConfigItemEntityId(oheId: "oheid1", id: "id1")
        def configItem = configurationItemRepository.save(new ConfigItemEntity(id: id, name: "name1", description: "desc1", type: "type1"))
        def page = configurationItemRepository.findAll(Pageable.from(0, 10))
        then:
        page
        page.content.size() == 1
        page.content[0].name == "name1"
        when:
        def cnt = configurationItemRepository.countByIdOheId(id.oheId)
        then:
        cnt == 1
        when:
        def idPredicate = new PredicateSpecification<ConfigItemEntity>() {
            @Override
            @Nullable Predicate toPredicate(@NonNull Root< ConfigItemEntity > root, @NonNull CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.equal(root.get("id").get("oheId"), configItem.id.oheId)
            }
        };
        List<ConfigItemEntity> list = configurationItemRepository.findAll(idPredicate)
        then:
        list.size() == 1
        when:
        Page<ConfigItemEntity> newPage = configurationItemRepository.findAll(idPredicate, Pageable.from(0, 10))
        then:
        newPage.content.size() == 1
        cleanup:
        configurationItemRepository.deleteAll()
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

@JdbcRepository(dialect = Dialect.ANSI)
interface ItemGroupRepository extends CrudRepository<ItemGroup, Long> {

    @Override
    @Join(value = "items", type = Join.Type.LEFT_FETCH)
    public abstract Optional<ItemGroup> findById(@NotNull Long id);
}

@Embeddable
class ConfigItemEntityId {
    @MappedProperty("ohe_id")
    String oheId
    @MappedProperty("id")
    String id
}

@MappedEntity("CONFIGURATION_ITEM")
class ConfigItemEntity {
    @EmbeddedId
    ConfigItemEntityId id
    @Nullable
    String description
    String name
    String type
}


@JdbcRepository(dialect = Dialect.ANSI)
interface ConfigurationItemRepository extends PageableRepository<ConfigItemEntity, ConfigItemEntityId>,
        JpaSpecificationExecutor<ConfigItemEntity> {

    long countByIdOheId(String oheId)
}
