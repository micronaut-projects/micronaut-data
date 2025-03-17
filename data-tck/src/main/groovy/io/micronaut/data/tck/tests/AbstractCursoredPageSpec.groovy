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

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.PersonRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Function

abstract class AbstractCursoredPageSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    abstract PersonRepository getPersonRepository()

    abstract BookRepository getBookRepository()

    def setup() {

        // Create a repository that will look something like this:
        // id | name     | age
        // 1  | AAAAA00  | 1
        // 2  | AAAAA01  | 2
        // ...
        // 10 | AAAAA09  | 10
        // 11 | BBBBB00  | 1
        // ..
        // 260 | ZZZZZ09 | 10
        // 261 | AAAAA00 | 11
        // 262 | AAAAA01 | 12
        // ...
        List<Person> people = []
        3.times {
            ('A'..'Z').each { letter ->
                10.times { num ->
                    people << new Person(name: letter * 5 + String.format("%02d", num), age: it * 10 + num + 1)
                }
            }
        }

        personRepository.saveAll(people)
    }

    def cleanup() {
        personRepository.deleteAll()
    }

    void "test cursored pageable list for sorting #sorting"() {
        when: "10 people are paged"
        def pageable = CursoredPageable.from(10, sorting)
        Page<Person> page = personRepository.findAll(pageable)

        then: "The data is correct"
        page.content.size() == 10
        page.content.every() { it instanceof Person }
        page.content[0].name == name1
        page.content[1].name == name2
        page.totalSize == 780
        page.totalPages == 78
        page.getCursor(0).isPresent()
        page.getCursor(9).isPresent()
        page.hasNext()

        when: "The next page is selected"
        page = personRepository.findAll(page.nextPageable())

        then: "it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.content[0].name == name10
        page.content[9].name == name19
        page.content.size() == 10
        page.hasNext()
        page.hasPrevious()

        when: "The previous page is selected"
        pageable = page.previousPageable()
        page = personRepository.findAll(pageable)

        then: "it is correct"
        page.offset == 0
        page.pageNumber == 0
        page.content[0].name == name1
        page.content.size() == 10
        page.hasNext()
        page.hasPrevious()

        where:
        sorting                                                      | name1     | name2     | name10    | name19
        null                                                         | "AAAAA00" | "AAAAA01" | "BBBBB00" | "BBBBB09"
        Sort.of(Sort.Order.desc("id"))                               | "ZZZZZ09" | "ZZZZZ08" | "YYYYY09" | "YYYYY00"
        Sort.of(Sort.Order.asc("name"))                              | "AAAAA00" | "AAAAA00" | "AAAAA03" | "AAAAA06"
        Sort.of(Sort.Order.desc("name"))                             | "ZZZZZ09" | "ZZZZZ09" | "ZZZZZ06" | "ZZZZZ03"
        Sort.of(Sort.Order.asc("age"), Sort.Order.asc("name"))       | "AAAAA00" | "BBBBB00" | "KKKKK00" | "TTTTT00"
        Sort.of(Sort.Order.desc("age"), Sort.Order.asc("name"))      | "AAAAA09" | "BBBBB09" | "KKKKK09" | "TTTTT09"
    }

    void "test pageable list with row removal"() {
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
        Sort.of(Sort.Order.desc("id"))   | "ZZZZZ09" | "ZZZZZ08" | "YYYYY09" | "YYYYY00"
        Sort.of(Sort.Order.asc("name"))  | "AAAAA00" | "AAAAA00" | "AAAAA03" | "AAAAA06"
        Sort.of(Sort.Order.desc("name")) | "ZZZZZ09" | "ZZZZZ09" | "ZZZZZ06" | "ZZZZZ03"
    }

    void "test pageable list with row addition"() {
        when: "10 people are paged"
        def pageable = CursoredPageable.from(10, sorting)
        Page<Person> page = personRepository.retrieve(pageable)

        then: "The data is correct"
        page.content.size() == 10
        page.content[0].name == elem1
        page.content[1].name == elem2
        page.hasNext()

        when: "The next page is selected after deletion"
        personRepository.saveAll([
                new Person(name: "AAAAA00"), new Person(name: "AAAAA01"),
                new Person(name: "ZZZZZ08"), new Person(name: "ZZZZZ07")
        ])
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
        page.content[0].name == elem3
        page.content.size() == 10
        page.hasPrevious()

        when: "The second previous page is selected"
        page = personRepository.retrieve(page.previousPageable())

        then:
        page.offset == 0
        page.pageNumber == 0
        page.content[0].name == elem1
        page.content[1].name == elem2
        page.getCursor(1).isPresent()
        page.getCursor(2).isEmpty()
        page.content.size() == 2
        !page.hasPrevious()

        where:
        sorting                          | elem1     | elem2     | elem3     | elem10    | elem19
        Sort.of(Sort.Order.asc("name"))  | "AAAAA00" | "AAAAA00" | "AAAAA00" | "AAAAA03" | "AAAAA06"
        Sort.of(Sort.Order.desc("name")) | "ZZZZZ09" | "ZZZZZ09" | "ZZZZZ09" | "ZZZZZ06" | "ZZZZZ03"
    }

    void "test cursored pageable"(Function<Pageable, Page<Person>> resultFunction) {
        when: "People are searched for"
        def pageable = CursoredPageable.from(10, null)
        def page = resultFunction.apply(pageable)
        def page2 = personRepository.findPeople("A%", pageable)

        then: "The page is correct"
        page.offset == 0
        page.pageNumber == 0
        page.totalSize == 30
        page2.totalSize == page.totalSize
        var firstContent = page.content
        page.content.name.every{ it.startsWith("A") }

        when: "The next page is retrieved"
        page = resultFunction.apply(page.nextPageable())

        then: "it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.content.id != firstContent.id
        page.content.name.every{ it.startsWith("A") }

        when: "The previous page is selected"
        pageable = page.previousPageable()
        page = resultFunction.apply(pageable)

        then: "it is correct"
        page.offset == 0
        page.pageNumber == 0
        page.content.size() == 10
        page.content.id == firstContent.id
        page.content.name.every{ it.startsWith("A") }

        where:
        resultFunction << [
                            (cursoredPageable) -> personRepository.findByNameLike("A%", (Pageable) cursoredPageable),
                            (cursoredPageable) -> personRepository.findAll(PersonRepository.Specifications.nameLike("A%"), (Pageable) cursoredPageable),
                          ]
    }

    void "test find with left join"() {
        given:
        def books = bookRepository.saveAll([
                new Book(title: "Book 1", totalPages: 100),
                new Book(title: "Book 2", totalPages: 100)
        ])

        when:
        def page = bookRepository.findByTotalPagesGreaterThan(
                50, CursoredPageable.from(books.size(), null)
        )

        then:
        page.getContent().size() == books.size()
        page.getTotalSize() == books.size()

        cleanup:
        bookRepository.deleteAll()
    }

    void "test cursored pageable without page size"() {
        when: "People are searched for"
        def pageable = CursoredPageable.from(Sort.of(Sort.Order.desc("name")))
        def page = personRepository.findAll(PersonRepository.Specifications.nameLike("BBBB%"), pageable)

        then: "The page is correct"
        page.offset == 0
        page.pageNumber == 0
        page.totalSize == 30
        page.content
        !page.content.empty
        page.content.forEach { it -> it instanceof Person}

        when: "The next page is retrieved"
        page = personRepository.findAll(PersonRepository.Specifications.nameLike("BBBB%"), page.nextPageable())

        then: "it is correct"
        page.offset == 0
        page.pageNumber == 1
        page.totalSize == 30
        page.nextPageable().offset == 0
        page.nextPageable().number == 2
        page.content.empty
    }
}
