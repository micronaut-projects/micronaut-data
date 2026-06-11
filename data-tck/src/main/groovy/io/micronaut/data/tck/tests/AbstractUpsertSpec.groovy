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
package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.UpsertEntity
import io.micronaut.data.tck.repositories.UpsertEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractUpsertSpec extends Specification {

    abstract UpsertEntityRepository getUpsertEntityRepository()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    ApplicationContext getApplicationContext() {
        return context
    }

    void setup() {
        upsertEntityRepository.deleteAll()
    }

    void cleanup() {
        upsertEntityRepository.deleteAll()
    }

    void "upsert inserts and updates assigned ID entity"() {
        when:
        UpsertEntity inserted = upsertEntityRepository.upsert(new UpsertEntity(1L, "First", "Initial value"))

        then:
        inserted == new UpsertEntity(1L, "First", "Initial value")
        upsertEntityRepository.findById(1L).get() == inserted

        when:
        UpsertEntity updated = upsertEntityRepository.upsert(new UpsertEntity(1L, "Second", "Updated value"))

        then:
        updated == new UpsertEntity(1L, "Second", "Updated value")
        upsertEntityRepository.findById(1L).get() == updated
    }

    void "upsertAll inserts and updates assigned ID entities"() {
        when:
        List<UpsertEntity> inserted = upsertEntityRepository.upsertAll([
                new UpsertEntity(2L, "Batch first", "Initial first"),
                new UpsertEntity(3L, "Batch second", "Initial second")
        ]).toList()

        then:
        inserted as Set == [
                new UpsertEntity(2L, "Batch first", "Initial first"),
                new UpsertEntity(3L, "Batch second", "Initial second")
        ] as Set
        upsertEntityRepository.findById(2L).get() == new UpsertEntity(2L, "Batch first", "Initial first")
        upsertEntityRepository.findById(3L).get() == new UpsertEntity(3L, "Batch second", "Initial second")

        when:
        List<UpsertEntity> updated = upsertEntityRepository.upsertAll([
                new UpsertEntity(2L, "Batch first", "Updated first"),
                new UpsertEntity(3L, "Batch second", "Updated second")
        ]).toList()

        then:
        updated as Set == [
                new UpsertEntity(2L, "Batch first", "Updated first"),
                new UpsertEntity(3L, "Batch second", "Updated second")
        ] as Set
        upsertEntityRepository.findById(2L).get() == new UpsertEntity(2L, "Batch first", "Updated first")
        upsertEntityRepository.findById(3L).get() == new UpsertEntity(3L, "Batch second", "Updated second")
    }

    void "upsert annotation inserts and updates assigned ID entity"() {
        when:
        UpsertEntity inserted = upsertEntityRepository.put(new UpsertEntity(4L, "Annotated first", "Initial value"))

        then:
        inserted == new UpsertEntity(4L, "Annotated first", "Initial value")
        upsertEntityRepository.findById(4L).get() == inserted

        when:
        UpsertEntity updated = upsertEntityRepository.put(new UpsertEntity(4L, "Annotated second", "Updated value"))

        then:
        updated == new UpsertEntity(4L, "Annotated second", "Updated value")
        upsertEntityRepository.findById(4L).get() == updated
    }

    void "upsert annotation inserts and updates assigned ID entities"() {
        when:
        List<UpsertEntity> inserted = upsertEntityRepository.putAll([
                new UpsertEntity(5L, "Annotated batch first", "Initial first"),
                new UpsertEntity(6L, "Annotated batch second", "Initial second")
        ]).toList()

        then:
        inserted as Set == [
                new UpsertEntity(5L, "Annotated batch first", "Initial first"),
                new UpsertEntity(6L, "Annotated batch second", "Initial second")
        ] as Set
        upsertEntityRepository.findById(5L).get() == new UpsertEntity(5L, "Annotated batch first", "Initial first")
        upsertEntityRepository.findById(6L).get() == new UpsertEntity(6L, "Annotated batch second", "Initial second")

        when:
        List<UpsertEntity> updated = upsertEntityRepository.putAll([
                new UpsertEntity(5L, "Annotated batch first", "Updated first"),
                new UpsertEntity(6L, "Annotated batch second", "Updated second")
        ]).toList()

        then:
        updated as Set == [
                new UpsertEntity(5L, "Annotated batch first", "Updated first"),
                new UpsertEntity(6L, "Annotated batch second", "Updated second")
        ] as Set
        upsertEntityRepository.findById(5L).get() == new UpsertEntity(5L, "Annotated batch first", "Updated first")
        upsertEntityRepository.findById(6L).get() == new UpsertEntity(6L, "Annotated batch second", "Updated second")
    }
}
