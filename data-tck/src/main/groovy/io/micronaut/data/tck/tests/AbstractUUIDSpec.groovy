/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.data.tck.entities.UuidEntity
import io.micronaut.data.tck.entities.UuidNullEntity
import io.micronaut.data.tck.repositories.UuidNullRepository
import io.micronaut.data.tck.repositories.UuidRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractUUIDSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    abstract UuidRepository getUuidRepository()

    abstract UuidNullRepository getUuidNullRepository()

    void 'test insert and update with UUID'() {
        when:
            def test = uuidRepository.save(new UuidEntity("Fred"))

            def uuid = test.uuid
        then:
            uuid != null

        when:
            test = uuidRepository.findById(test.uuid).orElse(null)

        then:
            test.uuid != null
            test.uuid == uuid
            test.name == 'Fred'
            test.child == null

        when: "update of missing child shouldn't trigger an error"
            test = uuidRepository.update(test)

        then:
            test.uuid != null
            test.uuid == uuid
            test.name == 'Fred'
            test.child == null

        cleanup:
            uuidRepository.deleteAll()
    }


    void 'test insert and return uuid'() {
        when:
           def test = uuidRepository.save(new UuidEntity("Fred"))

            def uuid = test.uuid
        then:
            uuid != null

        when:
            test = uuidRepository.findUuidByName("Fred")

        then:
            test != null
            test == uuid

        cleanup:
            uuidRepository.deleteAll()
    }

    void 'test null uuid parameter'() {
        when:
            def uuid = UUID.randomUUID()
            uuidNullRepository.saveAll([
                new UuidNullEntity(uuid),
                new UuidNullEntity(null),
            ])
        then:
            uuidNullRepository.findByUuid(uuid).size() == 1
            uuidNullRepository.findByUuid(null).size() == 1
    }
}
