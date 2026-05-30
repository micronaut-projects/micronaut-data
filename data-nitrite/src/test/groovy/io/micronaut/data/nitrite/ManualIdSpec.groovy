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
package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.ManualIdEntity
import io.micronaut.data.nitrite.repository.ManualIdRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class ManualIdSpec extends Specification {

    @Inject
    ManualIdRepository repository

    void "test manual UUID preserved"() {
        given:
        UUID manualId = UUID.randomUUID()
        ManualIdEntity entity = new ManualIdEntity(manualId, "Test Entity")

        when:
        repository.save(entity)

        then:
        def found = repository.findById(manualId)
        found.isPresent()
        found.get().id == manualId
        found.get().name == "Test Entity"
    }

    void "test default UUID preserved"() {
        given:
        ManualIdEntity entity = new ManualIdEntity("Default ID Entity")
        UUID defaultId = entity.id

        when:
        repository.save(entity)

        then:
        def found = repository.findById(defaultId)
        found.isPresent()
        found.get().id == defaultId
    }

    void "test update with manual UUID"() {
        given:
        UUID manualId = UUID.randomUUID()
        ManualIdEntity entity = new ManualIdEntity(manualId, "Original Name")
        repository.save(entity)

        when:
        entity.name = "Updated Name"
        repository.update(entity)

        then:
        def found = repository.findById(manualId)
        found.isPresent()
        found.get().id == manualId
        found.get().name == "Updated Name"
    }
}
