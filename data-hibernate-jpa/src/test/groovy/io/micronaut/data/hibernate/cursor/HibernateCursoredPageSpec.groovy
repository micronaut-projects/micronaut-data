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
package io.micronaut.data.hibernate.cursor

import io.micronaut.data.hibernate.H2DBProperties
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.hibernate.cursor", transactional = false)
@H2DBProperties
class HibernateCursoredPageSpec extends Specification {

    @Inject
    @Shared
    CursorPersonRepository repository

    def setupSpec() {
        repository.saveAll((1..10).collect { new CursorPerson("person ${it.toString().padLeft(2, '0')}", 20 + it) })
    }

    void "a repository query method pages forward and backward with a cursor"() {
        when:
            def first = repository.findByAgeGreaterThan(22, CursoredPageable.from(3, Sort.of(Sort.Order.asc("name"))))

        then:
            first.content*.name == ["person 03", "person 04", "person 05"]
            first.hasNext()

        when:
            def second = repository.findByAgeGreaterThan(22, first.nextPageable())

        then:
            second.content*.name == ["person 06", "person 07", "person 08"]

        when:
            def back = repository.findByAgeGreaterThan(22, second.previousPageable())

        then:
            back.content*.name == ["person 03", "person 04", "person 05"]
    }

    void "a specification query rejects a cursored pageable"() {
        when:
            repository.findAll({ root, cb -> cb.greaterThan(root.get("age"), 22) } as PredicateSpecification<CursorPerson>,
                    CursoredPageable.from(3, Sort.of(Sort.Order.asc("name"))) as Pageable)

        then:
            def e = thrown(UnsupportedOperationException)
            e.message.contains("CURSOR_NEXT")
    }
}
