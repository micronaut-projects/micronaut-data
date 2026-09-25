/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Relation
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.jspecify.annotations.Nullable
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class H2EmbeddedProjectionSpec extends Specification {

    @Inject
    WarehouseRepository warehouseRepository

    void "test projection of nullable mutable embeddable with reserved word column"() {
        given:
        def withoutDock = warehouseRepository.save(new Warehouse(name: "No dock"))
        def withDock = warehouseRepository.save(new Warehouse(name: "Dock", dock: new Dock(code: "D1", order: 3)))

        when:"The embedded value is not set"
        def emptyDock = warehouseRepository.findDockById(withoutDock.id)

        then:"The projection is empty"
        emptyDock.isEmpty()

        when:"The embedded value is set"
        def dock = warehouseRepository.findDockById(withDock.id).orElse(null)

        then:"The projection contains the values, including the reserved word column"
        dock
        dock.code == "D1"
        dock.order == 3

        cleanup:
        warehouseRepository.deleteAll()
    }

    void "test projection of required empty mutable embeddable"() {
        given:
        def warehouse = warehouseRepository.save(new Warehouse(name: "Empty dock", requiredDock: new Dock()))

        expect:"A required embedded is present even when none of its values is set"
        warehouseRepository.findRequiredDockById(warehouse.id).isPresent()

        cleanup:
        warehouseRepository.deleteAll()
    }

    void "test projection of nullable immutable embeddable with nullable fields"() {
        given:
        def withoutDock = warehouseRepository.save(new Warehouse(name: "No immutable dock"))
        def withDock = warehouseRepository.save(new Warehouse(name: "Immutable dock", immutableDock: new ImmutableDock("I1", null)))

        expect:
        warehouseRepository.findImmutableDockById(withoutDock.id).isEmpty()
        warehouseRepository.findImmutableDockById(withDock.id).get().code == "I1"
        warehouseRepository.findImmutableDockById(withDock.id).get().order == null

        cleanup:
        warehouseRepository.deleteAll()
    }

    void "test criteria projection of nullable embeddable"() {
        given:
        def withoutDock = warehouseRepository.save(new Warehouse(name: "No dock"))
        def withDock = warehouseRepository.save(new Warehouse(name: "Dock", dock: new Dock(code: "D2", order: 5)))

        when:"The embedded value is set"
        def dock = warehouseRepository.findOne(dockById(withDock.id))

        then:
        dock.code == "D2"
        dock.order == 5

        when:"The embedded value is not set"
        warehouseRepository.findOne(dockById(withoutDock.id))

        then:"There is no result"
        thrown(EmptyResultException)

        cleanup:
        warehouseRepository.deleteAll()
    }

    private static CriteriaQueryBuilder<Dock> dockById(Long id) {
        return { cb ->
            def query = cb.createQuery(Dock)
            def root = query.from(Warehouse)
            query.select(root.get("dock")).where(cb.equal(root.get("id"), id))
            query
        } as CriteriaQueryBuilder<Dock>
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface WarehouseRepository extends CrudRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse> {

    Optional<Dock> findDockById(Long id)

    Optional<Dock> findRequiredDockById(Long id)

    Optional<ImmutableDock> findImmutableDockById(Long id)
}

@MappedEntity
class Warehouse {
    @Id
    @GeneratedValue
    Long id
    String name
    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("dock_")
    Dock dock
    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("required_dock_")
    Dock requiredDock
    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("immutable_dock_")
    ImmutableDock immutableDock
}

// Mutable embeddable, instantiated with its no-arg constructor
@Embeddable
class Dock {
    @Nullable
    String code
    // "order" is a reserved word, so the generated result alias must be escaped
    @Nullable
    Integer order
}

// Immutable embeddable, instantiated with its constructor
@Embeddable
class ImmutableDock {
    @Nullable
    final String code
    @Nullable
    final Integer order

    ImmutableDock(@Nullable String code, @Nullable Integer order) {
        this.code = code
        this.order = order
    }
}
