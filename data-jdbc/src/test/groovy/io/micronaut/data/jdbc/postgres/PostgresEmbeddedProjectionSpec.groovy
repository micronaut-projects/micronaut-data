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
package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
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
import io.micronaut.data.tck.entities.Address
import io.micronaut.data.tck.entities.Jurisdiction
import io.micronaut.data.tck.entities.Registration
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.data.tck.entities.Vehicle
import org.jspecify.annotations.Nullable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PostgresEmbeddedProjectionSpec extends Specification implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    PostgresProjectionRestaurantRepository restaurantRepository = applicationContext.getBean(PostgresProjectionRestaurantRepository)

    @Shared
    PostgresProjectionVehicleRepository vehicleRepository = applicationContext.getBean(PostgresProjectionVehicleRepository)

    @Shared
    PostgresWarehouseRepository warehouseRepository = applicationContext.getBean(PostgresWarehouseRepository)

    def cleanup() {
        restaurantRepository.deleteAll()
        vehicleRepository.deleteAll()
        warehouseRepository.deleteAll()
    }

    void "test embedded and nullable embedded projections"() {
        given:
        def withoutHq = restaurantRepository.save(new Restaurant("No HQ", new Address("Main St.", "1111")))
        def withHq = restaurantRepository.save(new Restaurant("With HQ", new Address("Second St.", "2222")))
        withHq.hqAddress = new Address("HQ St.", "3333")
        restaurantRepository.update(withHq)

        expect:
        restaurantRepository.findAddressById(withoutHq.id).street == "Main St."
        restaurantRepository.findAddressById(withoutHq.id).zipCode == "1111"
        restaurantRepository.findHqAddressById(withoutHq.id).isEmpty()
        restaurantRepository.findHqAddressById(withHq.id).get().street == "HQ St."
        restaurantRepository.findHqAddressById(withHq.id).get().zipCode == "3333"
    }

    void "test nested embedded projections"() {
        given:
        def vehicle = vehicleRepository.save(new Vehicle(
                name: "Van",
                firstRegistration: new Registration(plateNumber: "ABC-123", status: "ACTIVE",
                        jurisdiction: new Jurisdiction(countryCode: "US", regionCode: "CA")),
                secondRegistration: new Registration(plateNumber: "XYZ-789", status: "EXPIRED",
                        jurisdiction: new Jurisdiction(countryCode: "CA", regionCode: "ON"))
        ))

        when:
        def second = vehicleRepository.findSecondRegistrationById(vehicle.id)
        def secondJurisdiction = vehicleRepository.findSecondRegistrationJurisdictionById(vehicle.id)
        def criteriaFirst = vehicleRepository.findOne(firstRegistrationById(vehicle.id))

        then:
        second.plateNumber == "XYZ-789"
        second.status == "EXPIRED"
        second.jurisdiction.countryCode == "CA"
        second.jurisdiction.regionCode == "ON"
        secondJurisdiction.countryCode == "CA"
        secondJurisdiction.regionCode == "ON"
        criteriaFirst.plateNumber == "ABC-123"
        criteriaFirst.jurisdiction.regionCode == "CA"
    }

    void "test projection with reserved word alias and nullability rules"() {
        given:
        def empty = warehouseRepository.save(new PostgresWarehouse(name: "Empty", requiredDock: new PostgresDock()))
        def full = warehouseRepository.save(new PostgresWarehouse(name: "Full",
                dock: new PostgresDock(code: "D1", order: 3),
                requiredDock: new PostgresDock(code: "R1", order: 1),
                immutableDock: new PostgresImmutableDock("I1", null)))

        expect:"Unset nullable embeddables are empty and a required one is present"
        warehouseRepository.findDockById(empty.id).isEmpty()
        warehouseRepository.findImmutableDockById(empty.id).isEmpty()
        warehouseRepository.findRequiredDockById(empty.id).isPresent()

        and:"Set values are projected, including the reserved word column"
        warehouseRepository.findDockById(full.id).get().code == "D1"
        warehouseRepository.findDockById(full.id).get().order == 3
        warehouseRepository.findRequiredDockById(full.id).get().code == "R1"
        warehouseRepository.findImmutableDockById(full.id).get().code == "I1"
        warehouseRepository.findImmutableDockById(full.id).get().order == null

        and:"Criteria projection follows the same rules"
        warehouseRepository.findOne(dockById(full.id)).order == 3
    }

    void "test criteria projection of unset nullable embeddable"() {
        given:
        def empty = warehouseRepository.save(new PostgresWarehouse(name: "Empty", requiredDock: new PostgresDock()))

        when:
        warehouseRepository.findOne(dockById(empty.id))

        then:
        thrown(EmptyResultException)
    }

    private static CriteriaQueryBuilder<Registration> firstRegistrationById(Long id) {
        return { cb ->
            def query = cb.createQuery(Registration)
            def root = query.from(Vehicle)
            query.select(root.get("firstRegistration")).where(cb.equal(root.get("id"), id))
            query
        } as CriteriaQueryBuilder<Registration>
    }

    private static CriteriaQueryBuilder<PostgresDock> dockById(Long id) {
        return { cb ->
            def query = cb.createQuery(PostgresDock)
            def root = query.from(PostgresWarehouse)
            query.select(root.get("dock")).where(cb.equal(root.get("id"), id))
            query
        } as CriteriaQueryBuilder<PostgresDock>
    }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresProjectionRestaurantRepository extends CrudRepository<Restaurant, Long> {

    Address findAddressById(Long id)

    Optional<Address> findHqAddressById(Long id)
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresProjectionVehicleRepository extends CrudRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    Registration findSecondRegistrationById(Long id)

    Jurisdiction findSecondRegistrationJurisdictionById(Long id)
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresWarehouseRepository extends CrudRepository<PostgresWarehouse, Long>, JpaSpecificationExecutor<PostgresWarehouse> {

    Optional<PostgresDock> findDockById(Long id)

    Optional<PostgresDock> findRequiredDockById(Long id)

    Optional<PostgresImmutableDock> findImmutableDockById(Long id)
}

@MappedEntity
class PostgresWarehouse {
    @Id
    @GeneratedValue
    Long id
    String name
    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("dock_")
    PostgresDock dock
    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("required_dock_")
    PostgresDock requiredDock
    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("immutable_dock_")
    PostgresImmutableDock immutableDock
}

// Mutable embeddable, instantiated with its no-arg constructor
@Embeddable
class PostgresDock {
    @Nullable
    String code
    // "order" is a reserved word, so the generated result alias must be escaped
    @Nullable
    Integer order
}

// Immutable embeddable, instantiated with its constructor
@Embeddable
class PostgresImmutableDock {
    @Nullable
    final String code
    @Nullable
    final Integer order

    PostgresImmutableDock(@Nullable String code, @Nullable Integer order) {
        this.code = code
        this.order = order
    }
}
