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
package io.micronaut.data.jdbc.sqlite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.tests.AbstractUUIDSpec
import io.micronaut.data.tck.repositories.UuidRepository
import spock.lang.AutoCleanup
import spock.lang.Shared

class SqliteUUIDSpec extends spock.lang.Specification implements SqliteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    SqliteUuidRepository uuidRepository = applicationContext.getBean(SqliteUuidRepository)

    void 'test insert and update with UUID'() {
        when:
        def test = uuidRepository.save(new SqliteUuidEntity("Fred"))
        def uuid = test.uuid

        then:
        uuid != null

        when:
        test = uuidRepository.findById(test.uuid).orElse(null)

        then:
        test.uuid == uuid
        test.name == 'Fred'

        when:
        test = uuidRepository.update(test)

        then:
        test.uuid == uuid
        test.name == 'Fred'

        cleanup:
        uuidRepository.deleteAll()
    }

    void 'test insert and return uuid'() {
        when:
        def test = uuidRepository.save(new SqliteUuidEntity("Fred"))
        def uuid = test.uuid

        then:
        uuid != null

        when:
        test = uuidRepository.findById(test.uuid).orElse(null)
        def foundUuid = uuidRepository.findUuidByName("Fred")

        then:
        test != null
        foundUuid == uuid

        cleanup:
        uuidRepository.deleteAll()
    }

    void 'test insert and update null uuid'() {
        when:
        def test = uuidRepository.save(new SqliteUuidEntity("Fred", UUID.randomUUID()))
        def uuid = test.uuid

        then:
        uuid != null
        test.uuid != null

        when:
        test.nullableValue = null
        def updatedTest = uuidRepository.update(test)

        then:
        updatedTest != null
        updatedTest.nullableValue == null

        cleanup:
        uuidRepository.deleteAll()
    }

    void 'test criteria with null value'() {
        when:
        uuidRepository.save(new SqliteUuidEntity("Fred", null))
        def result = uuidRepository.findByNullableValue(null)

        then:
        result.size() == 1

        cleanup:
        uuidRepository.deleteAll()
    }
}

class SQLiteUUIDSpec extends AbstractUUIDSpec implements SQLiteTestPropertyProvider {

    UuidRepository uuidRepository = applicationContext.getBean(SqliteUuidRepository)
}
