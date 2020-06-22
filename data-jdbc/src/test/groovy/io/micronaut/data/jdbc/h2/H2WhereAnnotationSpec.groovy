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
package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2WhereAnnotationSpec extends Specification {

    @Inject
    @Shared H2EnabledPersonRepository personRepository

    void setupSpec() {
        personRepository.deleteAll()
    }

    void cleanupSpec() {
        personRepository.deleteAll()
    }

    void "test return only enabled people"() {
        given:
        personRepository.saveAll([
                new Person(name: "Fred", age:35),
                new Person(name: "Joe", age:30, enabled: false),
                new Person(name: "Bob", age:30)
        ])

        expect:
        personRepository.count() == 2
        personRepository.countByNameLike("%e%") == 1
        !personRepository.findAll().any({ it.name == "Joe" })

    }
}
