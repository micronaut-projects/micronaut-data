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


import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(rollback = false)
@H2DBProperties
class EscapeIdentifiersSpec extends Specification{

    @Inject
    @Shared
    H2TableRatingsRepository repository

    void "test save one"() {
        when:"one is saved"
        def ratings = new TableRatings(10)
        repository.save(ratings)

        then:"the instance is persisted"
        ratings.id != null
        repository.findById(ratings.id).isPresent()
        repository.existsById(ratings.id)
        repository.count() == 1
        repository.findAll().size() == 1
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = repository.save(new TableRatings(20))
        def p2 = repository.save(new TableRatings(30))
        def ratings = [p1,p2]

        then:"all are saved"
        ratings.every { it.id != null }
        ratings.every { repository.findById(it.id).isPresent() }
        repository.findAll().size() == 3
        repository.count() == 3
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def rating = repository.findByRating(20)

        then:"the person is not null"
        rating != null
        rating.rating == 20
        repository.findById(rating.id).isPresent()

        when:"the person is deleted"
        repository.deleteById(rating.id)

        then:"They are really deleted"
        !repository.findById(rating.id).isPresent()
        repository.count() == 2
    }

    void "test update one"() {
        when:"A person is retrieved"
        def ratings = repository.findByRating(10)

        then:"The person is present"
        ratings != null

        when:"The person is updated"
        repository.updateRating(ratings.id, 15)

        then:"the person is updated"
        repository.findByRating(10) == null
        repository.findByRating(15) != null
    }

}
