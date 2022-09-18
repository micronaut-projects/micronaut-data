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
package io.micronaut.data.jdbc.postgres

import groovy.transform.Memoized
import io.micronaut.data.tck.entities.UuidEntity
import io.micronaut.data.tck.tests.AbstractUUIDSpec

class PostgresUUIDSpec extends AbstractUUIDSpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    PostgresUuidRepository getUuidRepository() {
        return applicationContext.getBean(PostgresUuidRepository)
    }

    void 'test multiple assignment'() {
        when:
        uuidRepository.save(new UuidEntity("Fred", null))
        def result = uuidRepository.findByNullableValueNative(null)
        then:
        result.size() == 1

        cleanup:
        uuidRepository.deleteAll()
    }
}
