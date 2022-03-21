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
package example  // Keep example package for testing non micronaut package

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MongoEmbeddedSpec2 extends Specification implements MongoTestPropertyProvider {

    @Inject
    @Shared
    RestaurantRepository2 restaurantRepository

    def cleanup() {
        restaurantRepository.deleteAll()
    }

    void "test save and retreive entity with embedded"() {
        when:"An entity is saved"
        restaurantRepository.saveAll([new Restaurant2("Fred's Cafe", new Address2("High St.", "7896"))])
        def restaurant = restaurantRepository.save(new Restaurant2("Joe's Cafe", new Address2("Smith St.", "1234")))

        then:"The entity was saved"
        restaurant
        restaurant.id
        restaurant.address.street == 'Smith St.'
        restaurant.address.zipCode == '1234'

        when:"The entity is retrieved"
        restaurant = restaurantRepository.findById(restaurant.id).orElse(null)

        then:"The embedded is populated correctly"
        restaurant.id
        restaurant.address.street == 'Smith St.'
        restaurant.address.zipCode == '1234'
        restaurant.hqAddress == null

        when:"The object is updated with non-null value"
        restaurant.hqAddress = new Address2("John St.", "4567")
        restaurantRepository.update(restaurant)
        restaurant = restaurantRepository.findById(restaurant.id).orElse(null)

        then:"The retrieved association is no longer null"
        restaurant.id
        restaurant.address
        restaurant.hqAddress
        restaurant.hqAddress.street == "John St."

        when:"A query is done by an embedded object"
        restaurant = restaurantRepository.findByAddress(restaurant.address)

        then:"The correct query is executed"
        restaurant.address.street == 'Smith St.'

    }

}

@MongoRepository
interface RestaurantRepository2 extends CrudRepository<Restaurant2, String> {

    Restaurant2 findByAddress(Address2 address);
}

@MappedEntity
class Restaurant2 {

    @GeneratedValue
    @Id
    String id
    final String name

    @Relation(Relation.Kind.EMBEDDED)
    final Address2 address

    @Relation(Relation.Kind.EMBEDDED)
    @Nullable
    Address2 hqAddress

    Restaurant2(String name, Address2 address) {
        this.name = name
        this.address = address
    }

}

@Embeddable
class Address2 {
    final String street
    final String zipCode

    Address2(String street, String zipCode) {
        this.street = street
        this.zipCode = zipCode
    }
}
