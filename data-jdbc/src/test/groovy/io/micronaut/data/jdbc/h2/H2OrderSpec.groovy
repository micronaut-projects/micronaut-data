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


import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@H2DBProperties
class H2OrderSpec extends Specification {

    @Inject
    H2PersonRepository personRepository

    void "test order - case insensitive"() {
        given: 'some test data is created'
        personRepository.save(new Person(name: 'ABC4'))
        personRepository.save(new Person(name: 'abc3'))
        personRepository.save(new Person(name: 'abc2'))
        personRepository.save(new Person(name: 'ABC1'))

        when: 'the list is sorted with ignore case'
        def order = new Sort.Order("name", Sort.Order.Direction.ASC, true)
        def list = personRepository.list(Pageable.from(0, 10).order(order))

        then: 'the list is ordered with case insensitive sorting'
        list[0].name == 'ABC1'
        list[1].name == 'abc2'
        list[2].name == 'abc3'
        list[3].name == 'ABC4'
    }
}
