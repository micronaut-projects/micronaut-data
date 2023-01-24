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
package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Shipment
import io.micronaut.data.tck.entities.ShipmentId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class JpaEmbeddedIdSpec extends Specification {

    @Inject
    JpaShipmentRepository repository

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
