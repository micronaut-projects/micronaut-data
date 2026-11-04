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
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.async.AsyncCrudRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

@MicronautTest(transactional = false)
class H2AsyncReactivePageSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    H2JdbcReactivePagePersonRepository reactiveRepository

    @Inject
    H2JdbcAsyncPagePersonRepository asyncRepository

    void setup() {
        reactiveRepository.deleteAll().block()
        reactiveRepository.saveAll((1..30).collect { new Person(name: "Person" + it, age: it) }).collectList().block()
    }

    void cleanup() {
        reactiveRepository.deleteAll().block()
    }

    void "test reactive cursored page"() {
        when:
            CursoredPage<Person> page = reactiveRepository.findByNameLike("Person%", CursoredPageable.from(10, Sort.of(Sort.Order.asc("age")))).block()

        then:
            page.content*.age == (1..10).toList()
            page.totalSize == 30
            page.cursors.size() == 10

        when:
            page = reactiveRepository.findByNameLike("Person%", page.nextPageable()).block()

        then:
            page.content*.age == (11..20).toList()
            page.totalSize == 30

        when:
            page = reactiveRepository.findByNameLike("Person%", page.nextPageable().withoutTotal()).block()

        then:
            page.content*.age == (21..30).toList()
            !page.hasTotalSize()
    }

    void "test reactive cursored page with an offset pageable"() {
        when:
            def result = reactiveRepository.findByNameLike("Person%", Pageable.from(0, 10, Sort.of(Sort.Order.asc("age")))).block()

        then:
            result instanceof CursoredPage

        when:
            CursoredPage<Person> page = (CursoredPage<Person>) result

        then:
            page.content*.age == (1..10).toList()
            page.totalSize == 30
            page.cursors.size() == 10

        when:
            page = reactiveRepository.findByNameLike("Person%", page.nextPageable()).block()

        then:
            page.content*.age == (11..20).toList()
            page.totalSize == 30
    }

    void "test async cursored page with an offset pageable"() {
        when:
            def result = asyncRepository.findByNameLike("Person%", Pageable.from(0, 10, Sort.of(Sort.Order.asc("age")))).get()

        then:
            result instanceof CursoredPage

        when:
            CursoredPage<Person> page = (CursoredPage<Person>) result

        then:
            page.content*.age == (1..10).toList()
            page.totalSize == 30
            page.cursors.size() == 10

        when:
            page = asyncRepository.findByNameLike("Person%", page.nextPageable()).get()

        then:
            page.content*.age == (11..20).toList()
            page.totalSize == 30
    }

    void "test reactive page honours request total"() {
        when:
            Page<Person> page = reactiveRepository.findByAgeGreaterThan(10, Pageable.from(1, 5, Sort.of(Sort.Order.asc("age")))).block()

        then:
            page.content*.age == (16..20).toList()
            page.totalSize == 20

        when:
            page = reactiveRepository.findByAgeGreaterThan(10, Pageable.from(1, 5, Sort.of(Sort.Order.asc("age"))).withoutTotal()).block()

        then:
            page.content*.age == (16..20).toList()
            !page.hasTotalSize()
    }

    void "test async page honours request total"() {
        when:
            Page<Person> page = asyncRepository.findByAgeGreaterThan(10, Pageable.from(1, 5, Sort.of(Sort.Order.asc("age")))).get()

        then:
            page.content*.age == (16..20).toList()
            page.totalSize == 20

        when:
            page = asyncRepository.findByAgeGreaterThan(10, Pageable.from(1, 5, Sort.of(Sort.Order.asc("age"))).withoutTotal()).get()

        then:
            page.content*.age == (16..20).toList()
            !page.hasTotalSize()
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface H2JdbcReactivePagePersonRepository extends ReactorCrudRepository<Person, Long> {

    Mono<CursoredPage<Person>> findByNameLike(String name, Pageable pageable)

    Mono<Page<Person>> findByAgeGreaterThan(int age, Pageable pageable)
}

@JdbcRepository(dialect = Dialect.H2)
interface H2JdbcAsyncPagePersonRepository extends AsyncCrudRepository<Person, Long> {

    CompletableFuture<CursoredPage<Person>> findByNameLike(String name, Pageable pageable)

    CompletableFuture<Page<Person>> findByAgeGreaterThan(int age, Pageable pageable)
}
