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
package io.micronaut.data.hibernate.reactive


import io.micronaut.data.tck.entities.Shipment
import io.micronaut.data.tck.entities.ShipmentId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
class JpaEmbeddedIdSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    JpaShipmentRepository repository

    void "test CRUD"() {
        when:
        ShipmentId id = new ShipmentId("a", "b")
        repository.save(new Shipment(id, "test")).block()

        ShipmentId id2 = new ShipmentId("c", "d")
        repository.save(new Shipment(id2, "test2")).block()

        ShipmentId id3 = new ShipmentId("e", "f")
        repository.save(new Shipment(id3, "test3")).block()

        ShipmentId id4 = new ShipmentId("g", "h")
        repository.save(new Shipment(id4, "test4")).block()

        def entity = repository.findById(id).block()

        then:
        repository.count().block() == 4
        entity != null

        when:"the entity is updated"
        repository.findAndUpdate(id, {it.field = 'changed'}).block()
        entity = repository.findById(id).block()

        then:"The update completes correctly"
        entity != null
        entity.field == 'changed'
        entity.shipmentId.city == 'b'

        when:"The entity is deleted"
        repository.deleteById(id2).block()

        then:"The delete works"
        repository.count().block() == 3

        when:"The delete method is used"
        repository.findAndDelete(entity.getShipmentId()).block()

        then:"The delete method works"
        repository.count().block() == 2

        when:"Find all is used"
        def all = repository.findAll().collectList().block()

        then:"all is correct"
        all.size() == 2

        when:"Find by country"
        def foundByCountry = repository.findByShipmentIdCountry("g").block()

        then:
        foundByCountry.field == "test4"
        foundByCountry.shipmentId.country == "g"
        foundByCountry.shipmentId.city == "h"

        when:"Find by country and city"
        def foundByCountryAndCIty = repository.findByShipmentIdCountryAndShipmentIdCity("g", "h").block()

        then:
        foundByCountryAndCIty.field == "test4"
        foundByCountryAndCIty.shipmentId.country == "g"
        foundByCountryAndCIty.shipmentId.city == "h"

        when:"deleteAll is used with an iterable"
        repository.findAndDeleteAll(all.first().getShipmentId()).block()

        then:"The entities where deleted"
        repository.count().block() == 1

        when:"deleteAll is used"
        repository.deleteAll().block()

        then:"The entities where deleted"
        repository.count().block() == 0
    }
}
