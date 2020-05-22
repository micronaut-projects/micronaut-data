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

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Address
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject


@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2EmbeddedSpec extends Specification {

    @Inject
    @Shared
    H2RestaurantRepository restaurantRepository

    void "test save and retreive entity with embedded"() {
        when:"An entity is saved"
        restaurantRepository.save(new Restaurant("Fred's Cafe", new Address("High St.", "7896")))
        def restaurant = restaurantRepository.save(new Restaurant("Joe's Cafe", new Address("Smith St.", "1234")))

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
        restaurant.hqAddress = new Address("John St.", "4567")
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
