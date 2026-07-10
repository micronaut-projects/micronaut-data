package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.NitriteEmbAddress
import io.micronaut.data.nitrite.model.NitriteEmbRestaurant
import io.micronaut.data.nitrite.repository.NitriteEmbRestaurantRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteEmbeddedSpec extends Specification implements NitriteTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteEmbRestaurantRepository restaurantRepository = applicationContext.getBean(NitriteEmbRestaurantRepository)

    def cleanup() {
        restaurantRepository.deleteAll()
    }

    void "test save and retrieve entity with embedded"() {
        when:"An entity is saved"
        restaurantRepository.save(new NitriteEmbRestaurant("Fred's Cafe", new NitriteEmbAddress("High St.", "7896")))
        NitriteEmbRestaurant restaurant = restaurantRepository.save(new NitriteEmbRestaurant("Joe's Cafe", new NitriteEmbAddress("Smith St.", "1234")))

        then:"The entity was saved"
        restaurant
        restaurant.id
        restaurant.address.street == 'Smith St.'
        restaurant.address.zipCode == '1234'

        when:"The entity is retrieved"
        NitriteEmbRestaurant retrieved = restaurantRepository.findById(restaurant.id).orElse(null)

        then:"The embedded is populated correctly"
        retrieved.id
        retrieved.address.street == 'Smith St.'
        retrieved.address.zipCode == '1234'
        retrieved.hqAddress == null

        when:"The object is updated with non-null value"
        retrieved.hqAddress = new NitriteEmbAddress("John St.", "4567")
        restaurantRepository.update(retrieved)
        retrieved = restaurantRepository.findById(retrieved.id).orElse(null)

        then:"The retrieved association is no longer null"
        retrieved.id
        retrieved.address
        retrieved.hqAddress
        retrieved.hqAddress.street == "John St."

        when:"A query is done by an embedded object"
        retrieved = restaurantRepository.findByAddress(retrieved.address)

        then:"The correct query is executed"
        retrieved.address.street == 'Smith St.'
    }
}
