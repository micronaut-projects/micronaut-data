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
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.hibernate.SessionFactory
import spock.lang.Specification

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
class FindBySpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    SessionFactory sessionFactory

    @Inject
    PersonRepository personRepository

    @Inject
    PersonCrudRepository crudRepository

    void "test setup"() {
        expect:
        sessionFactory != null
    }

    void "test find by name"() {
        when:
        true

        then:
        crudRepository.findByName("Fred").block() == null
        !personRepository.findByName("Fred").blockOptional().isPresent()

        when:
        crudRepository.save(new Person(name: "Fred")).block()
        crudRepository.saveAll([new Person(name: "Bob"), new Person(name: "Fredrick")]).collectList().block()
        Person p = personRepository.findByName("Bob").block()

        then:
        p != null
        p.name == "Bob"
        personRepository.findByName("Bob").blockOptional().isPresent()

        when:
        def results = personRepository.findAllByName("Bob").collectList().block()

        then:
        results.size() == 1
        results[0].name == 'Bob'

        when:
        results = personRepository.findAllByNameLike("Fred%", Pageable.from(0, 10)).collectList().block()

        then:
        results.size() == 2
    }
}
