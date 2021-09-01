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


import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject
import java.util.stream.Collectors

@MicronautTest
@H2DBProperties
class H2StreamingStatementSpec extends Specification {

    @Inject
    H2PersonRepository personRepository

    void "test streaming order"() {
        personRepository.save(new Person(name: 'a'))
        personRepository.save(new Person(name: 'c'))
        personRepository.save(new Person(name: 'b'))
        personRepository.save(new Person(name: 'd'))

        when:
            def list = personRepository.findAllAndStream().collect(Collectors.toList())

        then:
            list.size() == 4
            list[0].NAME == 'a'
            list[1].NAME == 'b'
            list[2].NAME == 'c'
            list[3].NAME == 'd'
    }
}
