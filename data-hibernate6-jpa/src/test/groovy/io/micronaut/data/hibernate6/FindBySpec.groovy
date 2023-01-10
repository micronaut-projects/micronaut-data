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
package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Property
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.hibernate.SessionFactory
import spock.lang.Specification

import jakarta.inject.Inject
import javax.sql.DataSource

@MicronautTest(packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class FindBySpec extends Specification {

    @Inject
    DataSource dataSource

    @Inject
    SessionFactory sessionFactory

    @Inject
    PersonRepository personRepository

    @Inject
    PersonCrudRepository crudRepository

    void "test setup"() {
        expect:
        dataSource != null
        sessionFactory != null
    }

    void "test find by name"() {
        when:
        Person p = personRepository.findByName("Fred")

        then:
        thrown(EmptyResultException)
        crudRepository.findByName("Fred") == null // declares nullable
        !personRepository.findOptionalByName("Fred").isPresent()

        when:
        crudRepository.save(new Person(name: "Fred"))
        crudRepository.saveAll([new Person(name: "Bob"), new Person(name: "Fredrick")])
        p = personRepository.findByName("Bob")

        then:
        p != null
        p.name == "Bob"
        personRepository.findOptionalByName("Bob").isPresent()

        when:
        def results = personRepository.findAllByName("Bob")

        then:
        results.size() == 1
        results[0].name == 'Bob'

        when:
        results = personRepository.findAllByNameLike("Fred%", Pageable.from(0, 10))

        then:
        results.size() == 2

    }
}
