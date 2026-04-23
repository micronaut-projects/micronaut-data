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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Shipment;
import io.micronaut.data.tck.entities.ShipmentId;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.data.model.query.builder.sql.Dialect.ANSI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@JavaSQLiteDBProperties
class SQLiteEmbeddedIdTest {

    @Inject
    ShipmentRepository repository;

    @Inject
    ItemGroupRepository groupRepository;

    @Inject
    ConfigurationItemRepository configurationItemRepository;

    @Test
    void testEmptyOneToManyViaEmbeddedId() {
        ItemGroup itemGroup = new ItemGroup(1L);
        itemGroup.setSecondId(2L);
        groupRepository.save(itemGroup);

        ItemGroup entity = groupRepository.findById(1L).orElseThrow();

        assertEquals(0, entity.getItems().size());
    }

    @Test
    void testCrud() {
        repository.deleteAll();

        ShipmentId id = new ShipmentId("a", "b");
        repository.save(new Shipment(id, "test"));

        ShipmentId id2 = new ShipmentId("c", "d");
        repository.save(new Shipment(id2, "test2"));

        ShipmentId id3 = new ShipmentId("e", "f");
        repository.save(new Shipment(id3, "test3"));

        ShipmentId id4 = new ShipmentId("g", "h");
        repository.save(new Shipment(id4, "test4"));

        Shipment entity = repository.findById(id).orElse(null);

        assertEquals(4, repository.count());
        assertNotNull(entity);

        entity.setField("changed");
        repository.update(entity);
        entity = repository.findById(id).orElse(null);

        assertNotNull(entity);
        assertEquals("changed", entity.getField());
        assertEquals("b", entity.getShipmentId().getCity());

        repository.deleteById(id2);
        assertEquals(3, repository.count());

        repository.delete(entity);
        assertEquals(2, repository.count());

        List<Shipment> all = repository.findAll();
        assertEquals(2, all.size());

        Shipment foundByCountry = repository.findByShipmentIdCountry("g");
        assertEquals("test4", foundByCountry.getField());
        assertEquals("g", foundByCountry.getShipmentId().getCountry());
        assertEquals("h", foundByCountry.getShipmentId().getCity());

        Shipment foundByCountryAndCity = repository.findByShipmentIdCountryAndShipmentIdCity("g", "h");
        assertEquals("test4", foundByCountryAndCity.getField());
        assertEquals("g", foundByCountryAndCity.getShipmentId().getCountry());
        assertEquals("h", foundByCountryAndCity.getShipmentId().getCity());

        List<Shipment> foundAllOrderByCityDesc = repository.findAllOrderByShipmentIdCityDesc();
        List<Shipment> foundAllOrderByCountryCityDesc = repository.findAllOrderByShipmentIdCountryAndShipmentIdCityDesc();

        assertEquals(2, foundAllOrderByCityDesc.size());
        assertEquals("test4", foundAllOrderByCityDesc.get(0).getField());
        assertEquals(id4.getCountry(), foundAllOrderByCityDesc.get(0).getShipmentId().getCountry());
        assertEquals(id4.getCity(), foundAllOrderByCityDesc.get(0).getShipmentId().getCity());
        assertEquals("test3", foundAllOrderByCityDesc.get(1).getField());
        assertEquals(id3.getCountry(), foundAllOrderByCityDesc.get(1).getShipmentId().getCountry());
        assertEquals(id3.getCity(), foundAllOrderByCityDesc.get(1).getShipmentId().getCity());
        assertEquals(2, foundAllOrderByCountryCityDesc.size());
        assertEquals("test3", foundAllOrderByCountryCityDesc.get(0).getField());
        assertEquals("test4", foundAllOrderByCountryCityDesc.get(1).getField());

        List<Shipment> foundAllOrderByDynamic = repository.findAll(Sort.of(
            Sort.Order.desc("shipmentId.country"),
            Sort.Order.asc("shipmentId.city")
        ));

        assertEquals(2, foundAllOrderByDynamic.size());
        assertEquals("g", foundAllOrderByDynamic.get(0).getShipmentId().getCountry());
        assertEquals("h", foundAllOrderByDynamic.get(0).getShipmentId().getCity());
        assertEquals("e", foundAllOrderByDynamic.get(1).getShipmentId().getCountry());
        assertEquals("f", foundAllOrderByDynamic.get(1).getShipmentId().getCity());

        repository.deleteAll(List.of(all.getFirst()));
        assertEquals(1, repository.count());

        repository.deleteAll();
        assertEquals(0, repository.count());
    }

    @Test
    void testCriteriaOrderOfEmbedded() {
        repository.deleteAll();

        repository.save(new Shipment(new ShipmentId("a", "b"), "test"));
        repository.save(new Shipment(new ShipmentId("c", "d"), "test2"));
        repository.save(new Shipment(new ShipmentId("e", "f"), "test3"));
        repository.save(new Shipment(new ShipmentId("g", "h"), "test4"));

        Sort.Order.Direction sortDirection = Sort.Order.Direction.ASC;
        Pageable pageable = Pageable.UNPAGED.order(new Sort.Order("shipmentId.city", sortDirection, false));
        Page<Shipment> page = repository.findAll(pageable);

        assertEquals(4, page.getTotalSize());
        assertEquals("b", page.getContent().getFirst().getShipmentId().getCity());

        repository.deleteAll();
    }

    @Test
    void testCursoredPageable() {
        repository.save(new Shipment(new ShipmentId("c1", "a"), "test"));
        repository.save(new Shipment(new ShipmentId("c1", "b"), "test2"));
        repository.save(new Shipment(new ShipmentId("c1", "c"), "test3"));
        repository.save(new Shipment(new ShipmentId("c1", "d"), "test4"));
        repository.save(new Shipment(new ShipmentId("c2", "a1"), "test5"));

        CursoredPageable cursoredPageable = CursoredPageable.from(3, Sort.of());
        CursoredPage<Shipment> page = repository.findByShipmentIdCountry("c1", cursoredPageable);

        assertEquals(3, page.getContent().size());
        assertEquals(true, page.hasNext());

        page = repository.findByShipmentIdCountry("c1", page.nextPageable());

        assertEquals(1, page.getContent().size());
        assertEquals(false, page.hasNext());

        repository.deleteAll();
    }

    @Test
    void testPagination() {
        ConfigItemEntityId id = new ConfigItemEntityId();
        id.setOheId("oheid1");
        id.setId("id1");

        ConfigItemEntity entity = new ConfigItemEntity();
        entity.setId(id);
        entity.setName("name1");
        entity.setDescription("desc1");
        entity.setType("type1");

        ConfigItemEntity configItem = configurationItemRepository.save(entity);
        Page<ConfigItemEntity> page = configurationItemRepository.findAll(Pageable.from(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
        assertEquals("name1", page.getContent().getFirst().getName());

        long cnt = configurationItemRepository.countByIdOheId(id.getOheId());
        assertEquals(1, cnt);

        PredicateSpecification<ConfigItemEntity> idPredicate = new PredicateSpecification<>() {
            @Override
            public @Nullable Predicate toPredicate(@NonNull Root<ConfigItemEntity> root,
                                                   @NonNull CriteriaBuilder criteriaBuilder) {
                return criteriaBuilder.equal(root.get("id").get("oheId"), configItem.getId().getOheId());
            }
        };
        List<ConfigItemEntity> list = configurationItemRepository.findAll(idPredicate);
        assertEquals(1, list.size());

        Page<ConfigItemEntity> newPage = configurationItemRepository.findAll(idPredicate, Pageable.from(0, 10));
        assertEquals(1, newPage.getContent().size());

        configurationItemRepository.deleteAll();
    }
}

@Entity
class ItemGroup {

    @Id
    private Long id;
    private Long secondId;

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private Set<Item> items = new HashSet<>();

    ItemGroup() {
    }

    ItemGroup(Long id) {
        this.id = id;
    }

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    Long getSecondId() {
        return secondId;
    }

    void setSecondId(Long secondId) {
        this.secondId = secondId;
    }

    Set<Item> getItems() {
        return items;
    }

    void setItems(Set<Item> items) {
        this.items = items;
    }
}

@Entity
class Item {

    @EmbeddedId
    private ItemGroupId id;

    ItemGroupId getId() {
        return id;
    }

    void setId(ItemGroupId id) {
        this.id = id;
    }
}

@Introspected
@Embeddable
class ItemGroupId {

    private Long firstId;
    private Long secondId;

    ItemGroupId() {
    }

    ItemGroupId(Long firstId, Long secondId) {
        this.firstId = firstId;
        this.secondId = secondId;
    }

    Long getFirstId() {
        return firstId;
    }

    void setFirstId(Long firstId) {
        this.firstId = firstId;
    }

    Long getSecondId() {
        return secondId;
    }

    void setSecondId(Long secondId) {
        this.secondId = secondId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemGroupId that)) {
            return false;
        }
        return Objects.equals(firstId, that.firstId) && Objects.equals(secondId, that.secondId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstId, secondId);
    }
}

@JdbcRepository(dialect = ANSI)
interface ItemGroupRepository extends CrudRepository<ItemGroup, Long> {

    @Override
    @Join(value = "items", type = Join.Type.LEFT_FETCH)
    Optional<ItemGroup> findById(@NotNull Long id);
}

@Embeddable
class ConfigItemEntityId {

    @MappedProperty("ohe_id")
    private String oheId;

    @MappedProperty("id")
    private String id;

    String getOheId() {
        return oheId;
    }

    void setOheId(String oheId) {
        this.oheId = oheId;
    }

    String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConfigItemEntityId that)) {
            return false;
        }
        return Objects.equals(oheId, that.oheId) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oheId, id);
    }
}

@MappedEntity("CONFIGURATION_ITEM")
class ConfigItemEntity {

    @EmbeddedId
    private ConfigItemEntityId id;

    @Nullable
    private String description;
    private String name;
    private String type;

    ConfigItemEntityId getId() {
        return id;
    }

    void setId(ConfigItemEntityId id) {
        this.id = id;
    }

    @Nullable
    String getDescription() {
        return description;
    }

    void setDescription(@Nullable String description) {
        this.description = description;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getType() {
        return type;
    }

    void setType(String type) {
        this.type = type;
    }
}

@JdbcRepository(dialect = ANSI)
interface ConfigurationItemRepository extends PageableRepository<ConfigItemEntity, ConfigItemEntityId>, JpaSpecificationExecutor<ConfigItemEntity> {

    long countByIdOheId(String oheId);
}
