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
package io.micronaut.data.document.mongodb

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.document.mongodb.repositories.MongoRestaurantRepository
import io.micronaut.data.document.tck.entities.Address
import io.micronaut.data.document.tck.entities.Restaurant
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.jspecify.annotations.Nullable
import spock.lang.Specification

@MicronautTest
class MongoEmbeddedProjectionSpec extends Specification implements MongoTestPropertyProvider {

    @Inject
    ParcelRepository parcelRepository

    @Inject
    MongoRestaurantRepository restaurantRepository

    def cleanup() {
        parcelRepository.deleteAll()
        restaurantRepository.deleteAll()
    }

    void "test projection of unset embedded property with the same type as the embedded id"() {
        given:
        def withoutPrevious = parcelRepository.save(new Parcel(id: new ParcelKey(code: "A", version: 1), name: "First"))
        def withPrevious = parcelRepository.save(new Parcel(id: new ParcelKey(code: "B", version: 2), name: "Second",
                previousKey: new ParcelKey(code: "B", version: 1)))

        expect:"The unset property is empty rather than the id"
        parcelRepository.findPreviousKeyByName("First").isEmpty()
        parcelRepository.findPreviousKeyByName("Second").get().version == 1

        and:"The embedded id is still projected"
        parcelRepository.findIdByName("First").code == withoutPrevious.id.code
        parcelRepository.findIdByName("Second").code == withPrevious.id.code
    }

    void "test collection projection skips unset embedded property"() {
        given:
        restaurantRepository.save(new Restaurant("Twin", new Address("First St.", "1111")))
        restaurantRepository.save(new Restaurant("Twin", new Address("Second St.", "2222"), new Address("HQ St.", "3333")))

        when:
        def hqAddresses = restaurantRepository.findHqAddressByName("Twin")

        then:
        hqAddresses.size() == 1
        hqAddresses[0].street == "HQ St."
    }
}

@MongoRepository
interface ParcelRepository extends CrudRepository<Parcel, ParcelKey> {

    Optional<ParcelKey> findPreviousKeyByName(String name)

    ParcelKey findIdByName(String name)
}

@MappedEntity
class Parcel {
    @EmbeddedId
    ParcelKey id
    String name
    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    ParcelKey previousKey
}

@Embeddable
class ParcelKey {
    String code
    Integer version
}
