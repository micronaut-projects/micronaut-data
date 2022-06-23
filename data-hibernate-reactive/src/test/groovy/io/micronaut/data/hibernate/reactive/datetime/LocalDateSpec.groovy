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
package io.micronaut.data.hibernate.reactive.datetime

import io.micronaut.data.hibernate.reactive.PostgresHibernateReactiveProperties
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class LocalDateSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject LocalDateTestRepository repository

    void "test sort by date created"() {
        when:
        repository.save("Fred").block()
        repository.save("Bob").block()

        then:
        repository.count().block() == 2
        repository.findAll(Pageable.from(Sort.of(Sort.Order.asc("name")))).block().first().name == "Bob"
        repository.findAll(Pageable.from(Sort.of(Sort.Order.desc("createdDate")))).block().content*.createdDate != null
        repository.findAll(Pageable.from(Sort.of(Sort.Order.desc("createdDate")))).block().first().name == "Fred"
    }
}
