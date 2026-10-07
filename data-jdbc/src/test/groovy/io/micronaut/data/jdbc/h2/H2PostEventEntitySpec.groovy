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
package io.micronaut.data.jdbc.h2

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class H2PostEventEntitySpec extends Specification implements H2TestPropertyProvider {

    @Inject
    PostEventRecordRepository repository

    void cleanup() {
        repository.deleteAll()
    }

    void "test post persist listener can replace a single entity"() {
        when:
            def saved = repository.save(new PostEventRecord(null, "single", null))

        then:
            saved.id() != null
            saved.status() == PostEventRecordListener.STATUS
    }

    void "test post persist listener can replace batch entities"() {
        when:
            def saved = repository.saveAll([new PostEventRecord(null, "a", null), new PostEventRecord(null, "b", null)])

        then:
            saved.size() == 2
            saved.every { it.id() != null && it.status() == PostEventRecordListener.STATUS }
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface PostEventRecordRepository extends CrudRepository<PostEventRecord, Long> {
}
