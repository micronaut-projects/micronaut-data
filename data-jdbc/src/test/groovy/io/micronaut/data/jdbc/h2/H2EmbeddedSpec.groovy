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

import io.micronaut.data.tck.entities.Address
import io.micronaut.data.tck.entities.Jurisdiction
import io.micronaut.data.tck.entities.Registration
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.data.tck.entities.Vehicle
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@H2DBProperties
class H2EmbeddedSpec extends Specification {

    @Inject
    @Shared
    H2RestaurantRepository restaurantRepository

    @Inject
    @Shared
    H2VehicleRepository vehicleRepository

    void "test save and retrieve entity with embedded"() {
        when:"An entity is saved"
        restaurantRepository.save(new Restaurant("Fred's Cafe", new Address("High St.", "7896")))
        def restaurant = restaurantRepository.save(new Restaurant("Joe's Cafe", new Address("Smith St.", "1234")))
        restaurantRepository.save(new Restaurant("Fred's Cafe", new Address("Main St.", "2201")))

        then:"The entity was saved"
        restaurant
        restaurant.id
        restaurant.address.street == 'Smith St.'
        restaurant.address.zipCode == '1234'

        when:"Find restaurant by street name"
        restaurant = restaurantRepository.findByAddressStreet("Smith St.").orElse(null)
        then:"Found restaurant"
        restaurant
        restaurant.name == "Joe's Cafe"

        when:"Max by embedded property"
        def maxStreet = restaurantRepository.getMaxAddressStreetByName("Fred's Cafe")
        def minStreet = restaurantRepository.getMinAddressStreetByName("Fred's Cafe")
        then:
        maxStreet == "Main St."
        minStreet == "High St."

        when:"The entity is retrieved"
        restaurant = restaurantRepository.findById(restaurant.id).orElse(null)

        then:"The embedded is populated correctly"
        restaurant.id
        restaurant.address.street == 'Smith St.'
        restaurant.address.zipCode == '1234'
        restaurant.hqAddress == null

        when:"Embedded field is projected as return type"
        def address = restaurantRepository.findAddressById(restaurant.id)
        def hqAddress = restaurantRepository.findHqAddressById(restaurant.id).orElse(null)

        then:"Address projection contains all fields and nullable hq projection is null"
        address
        address.street == 'Smith St.'
        address.zipCode == '1234'
        hqAddress == null

        when:"The object is updated with non-null value"
        restaurant.hqAddress = new Address("John St.", "4567")
        restaurantRepository.update(restaurant)
        restaurant = restaurantRepository.findById(restaurant.id).orElse(null)

        then:"The retrieved association is no longer null"
        restaurant.id
        restaurant.address
        restaurant.hqAddress
        restaurant.hqAddress.street == "John St."

        when:"Nullable embedded field is projected after it is set"
        hqAddress = restaurantRepository.findHqAddressById(restaurant.id).orElse(null)

        then:"Projected nullable embedded field contains all fields"
        hqAddress
        hqAddress.street == "John St."
        hqAddress.zipCode == "4567"

        when:"A query is done by an embedded object"
        restaurant = restaurantRepository.findByAddress(restaurant.address)

        then:"The correct query is executed"
        restaurant.address.street == 'Smith St.'
    }

    void "test save and retrieve nested embedded projections"() {
        given:"A vehicle with two embedded registrations and nested jurisdictions"
        def firstJurisdiction = new Jurisdiction()
        firstJurisdiction.countryCode = "US"
        firstJurisdiction.regionCode = "CA"
        def firstRegistration = new Registration()
        firstRegistration.plateNumber = "ABC-123"
        firstRegistration.status = "ACTIVE"
        firstRegistration.jurisdiction = firstJurisdiction

        def secondJurisdiction = new Jurisdiction()
        secondJurisdiction.countryCode = "CA"
        secondJurisdiction.regionCode = "ON"
        def secondRegistration = new Registration()
        secondRegistration.plateNumber = "XYZ-789"
        secondRegistration.status = "EXPIRED"
        secondRegistration.jurisdiction = secondJurisdiction

        def vehicle = new Vehicle()
        vehicle.name = "Delivery Van"
        vehicle.firstRegistration = firstRegistration
        vehicle.secondRegistration = secondRegistration

        when:"The vehicle is saved and embedded values are projected back"
        vehicle = vehicleRepository.save(vehicle)
        def projectedFirstRegistration = vehicleRepository.findFirstRegistrationById(vehicle.id)
        def projectedSecondRegistration = vehicleRepository.findSecondRegistrationById(vehicle.id)
        def projectedFirstJurisdiction = vehicleRepository.findFirstRegistrationJurisdictionById(vehicle.id)
        def projectedSecondJurisdiction = vehicleRepository.findSecondRegistrationJurisdictionById(vehicle.id)
        def criteriaFirstRegistration = vehicleRepository.findOne(H2VehicleRepository.Specifications.findFirstRegistrationById(vehicle.id))

        then:"Top-level embedded projections contain nested embedded values"
        projectedFirstRegistration
        projectedFirstRegistration.plateNumber == "ABC-123"
        projectedFirstRegistration.status == "ACTIVE"
        projectedFirstRegistration.jurisdiction
        projectedFirstRegistration.jurisdiction.countryCode == "US"
        projectedFirstRegistration.jurisdiction.regionCode == "CA"

        criteriaFirstRegistration
        criteriaFirstRegistration.plateNumber == "ABC-123"
        criteriaFirstRegistration.status == "ACTIVE"
        criteriaFirstRegistration.jurisdiction
        criteriaFirstRegistration.jurisdiction.countryCode == "US"
        criteriaFirstRegistration.jurisdiction.regionCode == "CA"

        projectedSecondRegistration
        projectedSecondRegistration.plateNumber == "XYZ-789"
        projectedSecondRegistration.status == "EXPIRED"
        projectedSecondRegistration.jurisdiction
        projectedSecondRegistration.jurisdiction.countryCode == "CA"
        projectedSecondRegistration.jurisdiction.regionCode == "ON"

        and:"Nested embedded field can be projected directly"
        projectedFirstJurisdiction
        projectedFirstJurisdiction.countryCode == "US"
        projectedFirstJurisdiction.regionCode == "CA"
        projectedSecondJurisdiction
        projectedSecondJurisdiction.countryCode == "CA"
        projectedSecondJurisdiction.regionCode == "ON"
    }
}
