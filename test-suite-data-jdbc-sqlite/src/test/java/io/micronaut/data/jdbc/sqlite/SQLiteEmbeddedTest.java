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

import io.micronaut.data.tck.entities.Address;
import io.micronaut.data.tck.entities.Restaurant;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
@SQLiteDBProperties
class SQLiteEmbeddedTest {

    @Inject
    SQLiteRestaurantRepository restaurantRepository;

    @Test
    void testSaveAndRetrieveEntityWithEmbedded() {
        restaurantRepository.save(new Restaurant("Fred's Cafe", new Address("High St.", "7896")));
        Restaurant restaurant = restaurantRepository.save(new Restaurant("Joe's Cafe", new Address("Smith St.", "1234")));
        restaurantRepository.save(new Restaurant("Fred's Cafe", new Address("Main St.", "2201")));

        assertNotNull(restaurant);
        assertNotNull(restaurant.getId());
        assertEquals("Smith St.", restaurant.getAddress().getStreet());
        assertEquals("1234", restaurant.getAddress().getZipCode());

        restaurant = restaurantRepository.findByAddressStreet("Smith St.").orElse(null);
        assertNotNull(restaurant);
        assertEquals("Joe's Cafe", restaurant.getName());

        String maxStreet = restaurantRepository.getMaxAddressStreetByName("Fred's Cafe");
        String minStreet = restaurantRepository.getMinAddressStreetByName("Fred's Cafe");
        assertEquals("Main St.", maxStreet);
        assertEquals("High St.", minStreet);

        restaurant = restaurantRepository.findById(restaurant.getId()).orElse(null);
        assertNotNull(restaurant);
        assertNotNull(restaurant.getId());
        assertEquals("Smith St.", restaurant.getAddress().getStreet());
        assertEquals("1234", restaurant.getAddress().getZipCode());
        assertNull(restaurant.getHqAddress());

        restaurant.setHqAddress(new Address("John St.", "4567"));
        restaurantRepository.update(restaurant);
        restaurant = restaurantRepository.findById(restaurant.getId()).orElse(null);

        assertNotNull(restaurant);
        assertNotNull(restaurant.getAddress());
        assertNotNull(restaurant.getHqAddress());
        assertEquals("John St.", restaurant.getHqAddress().getStreet());

        restaurant = restaurantRepository.findByAddress(restaurant.getAddress());
        assertEquals("Smith St.", restaurant.getAddress().getStreet());
    }
}
