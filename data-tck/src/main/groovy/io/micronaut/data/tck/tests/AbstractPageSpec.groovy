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
package io.micronaut.data.tck.tests

import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonRepository
import spock.lang.Specification

abstract class AbstractPageSpec extends Specification {

    abstract PersonRepository getPersonRepository()

    abstract void init()

    def setupSpec() {
        init()

        List<Person> people = []
        50.times { num ->
            ('A'..'Z').each {
                people << new Person(name: it * 5 + num)
            }
        }

        personRepository.saveAll(people)
    }

    def cleanupSpec() {
        personRepository.deleteAll()
    }

    void "test sort"() {
        when:"Sorted results are returned"
        def results = personRepository.listTop10(
                Sort.unsorted().order("name", Sort.Order.Direction.DESC)
        )

        then:"The results are correct"
        results.size() == 10
        results[0].name.startsWith("Z")
    }

    void "test pageable list"() {
        when:"All the people are count"
        def count = personRepository.count()

        then:"the count is correct"
        count == 1300

        when:"10 people are paged"
        def pageable = Pageable.from(0, 10)
        Page<Person> page = personRepository.findAll(pageable)

        then:"The data is correct"
        page.content.size() == 10
        page.content.every() { it instanceof Person }
        page.content[0].name.startsWith("A")
        page.content[1].name.startsWith("B")
        page.totalSize == 1300
        page.totalPages == 130
        page.nextPageable().offset == 10
        page.nextPageable().size == 10

        when:"The next page is selected"
        pageable = page.nextPageable()
        page = personRepository.findAll(pageable)

        then:"it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.content[0].name.startsWith("K")
        page.content.size() == 10

        when:"The previous page is selected"
        pageable = page.previousPageable()
        page = personRepository.findAll(pageable)

        then:"it is correct"
        page.offset == 0
        page.pageNumber == 0
        page.content[0].name.startsWith("A")
        page.content.size() == 10
    }

    void "test pageable sort"() {
        when:"All the people are count"
        def count = personRepository.count()

        then:"the count is correct"
        count == 1300

        when:"10 people are paged"
        Page<Person> page = personRepository.findAll(
                Pageable.from(0, 10)
                        .order("name", Sort.Order.Direction.DESC)
        )

        then:"The data is correct"
        page.content.size() == 10
        page.content.every() { it instanceof Person }
        page.content[0].name.startsWith("Z")
        page.content[1].name.startsWith("Z")
        page.totalSize == 1300
        page.totalPages == 130
        page.nextPageable().offset == 10
        page.nextPageable().size == 10

        when:"The next page is selected"
        page = personRepository.findAll(page.nextPageable())

        then:"it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.content[0].name.startsWith("Z")
    }


    void "test pageable findBy"() {
        when:"People are searched for"
        def pageable = Pageable.from(0, 10)
        Page<Person> page = personRepository.findByNameLike("A%", pageable)
        Page<Person> page2 = personRepository.findPeople("A%", pageable)
        Slice<Person> slice = personRepository.queryByNameLike("A%", pageable)

        then:"The page is correct"
        page.offset == 0
        page.pageNumber == 0
        page.totalSize == 50
        page2.totalSize == page.totalSize
        slice.offset == 0
        slice.pageNumber == 0
        slice.size == 10
        slice.content
        page.content

        when:"The next page is retrieved"
        page = personRepository.findByNameLike("A%", page.nextPageable())

        then:"it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.totalSize == 50
        page.nextPageable().offset == 20
        page.nextPageable().number == 2

    }
}
