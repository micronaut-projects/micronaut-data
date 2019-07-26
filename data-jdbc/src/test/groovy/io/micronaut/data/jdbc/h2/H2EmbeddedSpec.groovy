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
    }
}
