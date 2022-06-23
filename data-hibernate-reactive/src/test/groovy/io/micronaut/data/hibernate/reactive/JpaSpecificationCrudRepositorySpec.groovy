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
package io.micronaut.data.hibernate.reactive

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.data.hibernate.reactive.JpaSpecificationCrudRepository.Specifications.*

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
class JpaSpecificationCrudRepositorySpec extends Specification implements PostgresHibernateReactiveProperties {
    @Inject
    @Shared
    JpaSpecificationCrudRepository crudRepository

    def setupSpec() {
        crudRepository.saveAll([
                new Person(name: "Jeff", age: 50),
                new Person(name: "James", age: 35)
        ]).then().block()
        def person = new Person(name: "Fred", age: 40)
        crudRepository.save(person).block()
        def p1 = new Person(name: "Frank", age: 20)
        def p2 = new Person(name: "Bob", age: 45)
        def people = [p1, p2]
        crudRepository.saveAll(people).then().block()
    }

    void "test JPA specification count"() {
        expect:
        crudRepository.count(ageGreaterThanThirty()).block() == 4
        def results = crudRepository.findAll(ageGreaterThanThirty()).collectList().block()
        results.size() == 4
        results.every({ it instanceof Person})

        def sorted = crudRepository.findAll(ageGreaterThanThirty(), Sort.of(Sort.Order.asc("age"))).collectList().block()

        sorted.first().name == "James"
        sorted.last().name == "Jeff"

        crudRepository.findOne(nameEquals("James")).block().name == "James"
        def page2Req = Pageable.from(1, 2, Sort.of(Sort.Order.asc("age")))
        def page1Req = Pageable.from(0, 2, Sort.of(Sort.Order.asc("age")))
        def page1 = crudRepository.findAll(ageGreaterThanThirty(), page1Req).block()
        def page2 = crudRepository.findAll(ageGreaterThanThirty(), page2Req).block()
        page2.size == 2
        page2.content*.name == ["Bob", "Jeff"]
        page1.size == 2
        page1.content*.name == ["James", "Fred"]
    }

}
