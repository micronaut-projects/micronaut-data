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

import groovy.transform.Memoized
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractCursoredPageSpec

class H2CursoredPaginationSpec extends AbstractCursoredPageSpec implements H2TestPropertyProvider {

    @Memoized
    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(H2PersonRepository)
    }

    @Memoized
    @Override
    BookRepository getBookRepository() {
        return context.getBean(H2BookRepository)
    }

    void "test pageable list with row removal XX"() {
        when: "10 people are paged"
            def pageable = Pageable.from(0, 10, sorting) // The first pageable can be non-cursored
            Page<Person> page = personRepository.retrieve(pageable) // The retrieve method explicitly returns CursoredPage

        then: "The data is correct"
            page.content.size() == 10
            page.content[0].name == elem1
            page.content[1].name == elem2
            page.hasNext()

        when: "The next page is selected after deletion"
            personRepository.delete(page.content[1])
            personRepository.delete(page.content[9])
            page = personRepository.retrieve(page.nextPageable())

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.content[0].name == elem10
            page.content[9].name == elem19
            page.content.size() == 10
            page.hasNext()
            page.hasPrevious()

        when: "The previous page is selected"
            pageable = page.previousPageable()
            page = personRepository.retrieve(pageable)

        then: "it is correct"
            page.offset == 0
            page.pageNumber == 0
            page.content[0].name == elem1
            page.content.size() == 8
            page.getCursor(7).isPresent()
            page.getCursor(8).isEmpty()
            !page.hasPrevious()
            page.hasNext()

        where:
            sorting                          | elem1     | elem2     | elem10    | elem19
            null                             | "AAAAA00" | "AAAAA01" | "BBBBB00" | "BBBBB09"
            Sort.of(Sort.Order.desc("id")) | "ZZZZZ09" | "ZZZZZ08" | "YYYYY09" | "YYYYY00"
            Sort.of(Sort.Order.asc("name"))  | "AAAAA00" | "AAAAA00" | "AAAAA03" | "AAAAA06"
            Sort.of(Sort.Order.desc("name")) | "ZZZZZ09" | "ZZZZZ09" | "ZZZZZ06" | "ZZZZZ03"
    }

}
