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

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoFindQuery
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A custom find query with an empty filter matches all the documents.
 */
class MongoEmptyFilterQuerySpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    EmptyFilterItemRepository repository = applicationContext.getBean(EmptyFilterItemRepository)

    void cleanup() {
        repository.deleteAll()
    }

    void "custom find query with an empty filter returns all documents"() {
        given:
            repository.saveAll(["b", "c", "a"].collect { new EmptyFilterItem(name: it) })

        when:
            def sorted = repository.findAllSorted()

        then:
            sorted*.name == ["a", "b", "c"]
    }
}

@MongoRepository
interface EmptyFilterItemRepository extends CrudRepository<EmptyFilterItem, String> {

    @MongoFindQuery(filter = "", sort = "{ name : 1 }")
    List<EmptyFilterItem> findAllSorted()
}

@MappedEntity
class EmptyFilterItem {
    @Id
    @GeneratedValue
    String id

    String name
}
