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


import io.micronaut.data.tck.entities.Plant
import io.micronaut.data.tck.repositories.PlantRepository
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@H2DBProperties
class H2NullableConstructorSpec extends Specification {

    @Inject
    @Shared
    PlantRepository plantRepository

    void "test save and retrieve nullable association"() {
        when:
        def plant = plantRepository.save(new Plant("Orange", null))

        then:
        plant.id

        when:
        plant = plantRepository.findById(plant.id)

        then:
        plant.id
        plant.name == "Orange"
        plant.nursery == null
        plant.maxHeight == null
    }
}
