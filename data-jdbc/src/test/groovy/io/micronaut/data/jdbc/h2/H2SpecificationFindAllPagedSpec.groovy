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

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.nameLike

@MicronautTest(transactional = false)
class H2SpecificationFindAllPagedSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    H2AsyncSpecificationPersonRepository asyncRepository

    @Inject
    H2ReactiveSpecificationPersonRepository reactiveRepository

    void setup() {
        asyncRepository.deleteAll().get()
        asyncRepository.saveAll((1..30).collect { new Person(name: "Person" + it, age: it) }).get()
    }

    void cleanup() {
        asyncRepository.deleteAll().get()
    }

    void "test async find all by specification honours the page size and offset"() {
        when:
            def people = asyncRepository.findAllPaged(nameLike("Person%"), Pageable.from(2, 10, Sort.of(Sort.Order.asc("age")))).get()

        then:
            people*.age == (21..30).toList()
    }

    void "test reactive find all by specification honours the page size and offset"() {
        when:
            def people = reactiveRepository.findAllPaged(nameLike("Person%"), Pageable.from(2, 10, Sort.of(Sort.Order.asc("age")))).collectList().block()

        then:
            people*.age == (21..30).toList()
    }
}
