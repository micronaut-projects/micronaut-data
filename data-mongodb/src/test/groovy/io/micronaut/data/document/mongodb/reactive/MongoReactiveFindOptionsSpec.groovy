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
package io.micronaut.data.document.mongodb.reactive

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.operations.options.MongoFindOptions
import io.micronaut.data.mongodb.repository.MongoReactiveQueryExecutor
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Non-positive skip, limit and batch size find options are ignored, the same way as with the sync driver.
 */
class MongoReactiveFindOptionsSpec extends Specification implements MongoSelectReactiveDriver {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    ReactiveFindOptionsItemRepository repository = applicationContext.getBean(ReactiveFindOptionsItemRepository)

    void setup() {
        repository.saveAll(["a", "b", "c"].collect { new ReactiveFindOptionsItem(name: it) }).blockLast()
    }

    void cleanup() {
        repository.deleteAll().block()
    }

    void "non-positive find option #description is ignored"() {
        when:
            def items = repository.findAll(options).collectList().block()

        then:
            items*.name.sort() == ["a", "b", "c"]

        where:
            description     | options
            "skip 0"        | new MongoFindOptions().skip(0)
            "skip -1"       | new MongoFindOptions().skip(-1)
            "limit 0"       | new MongoFindOptions().limit(0)
            "limit -1"      | new MongoFindOptions().limit(-1)
            "batch size 0"  | new MongoFindOptions().batchSize(0)
            "batch size -1" | new MongoFindOptions().batchSize(-1)
    }
}

@MongoRepository
interface ReactiveFindOptionsItemRepository extends ReactorCrudRepository<ReactiveFindOptionsItem, String>, MongoReactiveQueryExecutor<ReactiveFindOptionsItem> {
}

@MappedEntity
class ReactiveFindOptionsItem {
    @Id
    @GeneratedValue
    String id

    String name
}
