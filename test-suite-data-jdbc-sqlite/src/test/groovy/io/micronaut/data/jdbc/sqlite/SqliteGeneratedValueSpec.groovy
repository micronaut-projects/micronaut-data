/*
 * Copyright 2017-2025 original authors
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
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SqliteGeneratedValueSpec extends Specification implements SQLiteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    SqliteGeneratedValueRepository repository = context.getBean(SqliteGeneratedValueRepository)

    void "test save and load generated identity"() {
        when:
        def saved = repository.save(new SqliteGeneratedValueEntity("alpha"))

        then:
        saved.id != null

        when:
        def reloaded = repository.findById(saved.id).orElse(null)

        then:
        reloaded != null
        reloaded.id == saved.id
        reloaded.name == "alpha"

        cleanup:
        repository.deleteAll()
    }

    void "test saveAll assigns generated identities"() {
        when:
        def saved = repository.saveAll([
                new SqliteGeneratedValueEntity("alpha"),
                new SqliteGeneratedValueEntity("beta")
        ]).toList()

        then:
        saved*.id.every { it != null }
        saved*.id.unique().size() == 2

        when:
        def reloaded = saved.collect { repository.findById(it.id).orElse(null) }

        then:
        reloaded*.name.toSet() == ["alpha", "beta"] as Set

        cleanup:
        repository.deleteAll()
    }
}
