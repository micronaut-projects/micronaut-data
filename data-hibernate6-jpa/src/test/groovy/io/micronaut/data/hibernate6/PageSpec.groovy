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
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(rollback = false, transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class PageSpec extends Specification {

    @Inject
    @Shared
    PersonRepository personRepository

    @Inject
    @Shared
    PersonCrudRepository crudRepository

    @Inject
    @Shared
    BookRepository bookRepository

    def setup() {
        populate()
    }

    def cleanup() {
        crudRepository.deleteAll()
    }

    private void populate() {
        List<Person> people = []
        50.times { num ->
            ('A'..'Z').each {
                //  age doesn't follow the name ordering so we can test sorting
                people << new Person(name: it * 5 + num, age: (49 - num) % 25)
            }
        }
        crudRepository.saveAll(people)
    }

    void "test reactive single result that returns pageable"() {
        when:
        def page = Mono.from(crudRepository.find(10, Pageable.from(1))).block()

        then:
        page.size == 10
        page.totalSize == 728
        page.totalPages == 73
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

    void "test unpaged pageable with order"() {
        when:
        def results = personRepository.list(Pageable.UNPAGED.order(Sort.Order.asc("name")))

        then:
        results.size() == 1300
        results.first().name == "AAAAA0"
        results.last().name == "ZZZZZ9"

        when:
        def next = Pageable.UNPAGED.next()

        then:
        next.size == -1
        next.number == 0

        when:
        def previous = Pageable.UNPAGED.previous()

        then:
        previous.size == -1
        previous.number == 0
    }

    void "test sortMultiple"() {
        when: "Sorted Age ASC Name DESC results are returned"
        def results = personRepository.listTop10(
                Sort.unsorted().order("age", Sort.Order.Direction.ASC)
                        .order("name", Sort.Order.Direction.DESC)
        )

        then: "The results are correct"
        results.size() == 10
        results[0].name.equals("ZZZZZ49")
        results[1].name.equals("ZZZZZ24")
        results[2].name.equals("YYYYY49")
        results[3].name.equals("YYYYY24")

        when: "Sorted Age DESC Name ASC results are returned"
        results = personRepository.listTop10(
                Sort.unsorted().order("age", Sort.Order.Direction.DESC)
                        .order("name", Sort.Order.Direction.ASC)
        )

        then: "The results are correct"
        results.size() == 10
        results[0].name.equals("AAAAA0")
        results[1].name.equals("AAAAA25")
        results[2].name.equals("BBBBB0")
        results[3].name.equals("BBBBB25")
    }

    void "test pageable list"() {
        when:"All the people are count"
        def count = crudRepository.count()

        then:"the count is correct"
        count == 1300

        when:"10 people are paged"
        Page<Person> page = personRepository.list(Pageable.from(0, 10))

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
        page = crudRepository.findAll(page.nextPageable())

        then:"it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.content[0].name.startsWith("K")
    }

    void "test pageable sort"() {
        when: "All the people are count"
        def count = crudRepository.count()

        then: "the count is correct"
        count == 1300

        when: "10 people are paged"
        Page<Person> page = personRepository.list(
                Pageable.from(0, 10)
                        .order("name", Sort.Order.Direction.DESC)
        )

        then: "The data is correct"
        page.content.size() == 10
        page.content.every() { it instanceof Person }
        page.content[0].name.startsWith("Z")
        page.content[1].name.startsWith("Z")
        page.totalSize == 1300
        page.totalPages == 130
        page.nextPageable().offset == 10
        page.nextPageable().size == 10

        when: "The next page is selected"
        page = crudRepository.findAll(page.nextPageable())

        then: "it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.content[0].name.startsWith("Z")
    }

    void "test pageable findBy"() {
        when:"People are searched for"
        def pageable = Pageable.from(0, 10)
        Page<Person> page = personRepository.findByNameLike("A%", pageable)
        Page<Person> page2 = crudRepository.findPeople("A%", pageable)
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

    void "test total size of find with join"() {
        given:
        def books = bookRepository.saveAll([
            new Book(title: "Book 1"),
            new Book(title: "Book 2")
        ])

        when:
        def page = bookRepository.findAll(Pageable.from(0, books.size()))

        then:
        page.getContent().isEmpty()
        page.getTotalSize() == 0
    }
}
