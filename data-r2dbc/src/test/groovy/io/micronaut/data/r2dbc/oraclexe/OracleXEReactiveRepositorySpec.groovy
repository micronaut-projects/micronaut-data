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
package io.micronaut.data.r2dbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.repositories.BookReactiveRepository
import io.micronaut.data.tck.repositories.PersonReactiveRepository
import io.micronaut.data.tck.repositories.StudentReactiveRepository
import io.micronaut.data.tck.tests.AbstractReactiveRepositorySpec

import java.time.LocalDateTime

class OracleXEReactiveRepositorySpec extends AbstractReactiveRepositorySpec implements OracleXETestPropertyProvider {

    @Memoized
    @Override
    OracleXEPersonReactiveRepository getPersonRepository() {
        return context.getBean(OracleXEPersonReactiveRepository)
    }

    @Memoized
    @Override
    StudentReactiveRepository getStudentRepository() {
        return context.getBean(OracleXEStudentReactiveRepository)
    }

    @Memoized
    @Override
    BookReactiveRepository getBookRepository() {
        return context.getBean(OracleReactiveBookRepository)
    }

    void "test procedure"() {
        expect:
            personRepository.add1(123).block() == 124
            personRepository.add1Aliased(123).block() == 124
    }

    void "test returning insert update and delete"() {
        given:
        def repository = context.getBean(OracleReactiveBookRepository)
        def existing = repository.save(new Book(title: "Oracle Existing", totalPages: 250)).block()
        def lastUpdated = LocalDateTime.now()
        def derivedBook = new Book(title: "Oracle Reactive Derived It", totalPages: 301, author: existing.author, lastUpdated: lastUpdated)
        def derivedBooksToSave = [
                new Book(title: "Oracle Reactive Derived Stand", totalPages: 101, author: existing.author, lastUpdated: lastUpdated),
                new Book(title: "Oracle Reactive Derived Shining", totalPages: 201, author: existing.author, lastUpdated: lastUpdated)
        ]

        when:
        def derivedSavedBook = repository.saveReturning(derivedBook).block()
        def derivedSavedBooks = repository.saveReturningAll(derivedBooksToSave).collectList().block()

        then:
        derivedSavedBook.id != null
        derivedSavedBook.title == "Oracle Reactive Derived It"
        derivedSavedBooks*.id.every { it != null }
        derivedSavedBooks*.id.toSet().size() == derivedSavedBooks.size()
        derivedSavedBooks*.title == ["Oracle Reactive Derived Stand", "Oracle Reactive Derived Shining"]

        when:
        def reloadedDerivedBook = repository.findById(derivedSavedBook.id).block()
        def reloadedDerivedSavedBooks = [
                repository.findById(derivedSavedBooks[0].id).block(),
                repository.findById(derivedSavedBooks[1].id).block()
        ]
        derivedSavedBook.title = "Oracle Reactive Derived It Updated"
        def updatedBook = repository.updateReturning(derivedSavedBook).block()

        then:
        reloadedDerivedBook.id == derivedSavedBook.id
        reloadedDerivedBook.title == "Oracle Reactive Derived It"
        reloadedDerivedSavedBooks*.id == derivedSavedBooks*.id
        reloadedDerivedSavedBooks*.title == derivedSavedBooks*.title
        updatedBook.id == derivedSavedBook.id
        updatedBook.title == "Oracle Reactive Derived It Updated"

        when:
        def reloadedUpdatedBook = repository.findById(updatedBook.id).block()

        then:
        reloadedUpdatedBook.id == updatedBook.id
        reloadedUpdatedBook.title == updatedBook.title

        when:
        def deletedBook = repository.deleteReturning(updatedBook).block()

        then:
        deletedBook.id == updatedBook.id
        deletedBook.title == "Oracle Reactive Derived It Updated"
    }

    void "test custom insert returning probe"() {
        given:
        def repository = context.getBean(OracleReactiveBookRepository)

        when:
        def base = repository.save(new Book(title: "Oracle Custom Base", totalPages: 77)).block()
        def saved = repository.customInsertReturningBook(base.author?.id ?: 0L, base.genre?.id ?: 0L, "Oracle Custom Returning", 123, base.publisher?.id ?: 0L, LocalDateTime.now()).block()

        then:
        saved != null
        saved.id != null
        saved.title == "Oracle Custom Returning"
    }

    void "test custom insert update and delete returning"() {
        given:
        def repository = context.getBean(OracleReactiveBookRepository)
        def existing = repository.save(new Book(title: "Oracle Existing Custom", totalPages: 250)).block()
        def lastUpdated = LocalDateTime.now()

        when:
        def insertedBook = repository.customInsertReturningBook(existing.author?.id ?: 0L, existing.genre?.id ?: 0L, "Oracle Query Coraline", 404, existing.publisher?.id ?: 0L, lastUpdated).block()
        def insertedBooks = repository.customInsertReturningBooks(existing.author?.id ?: 0L, existing.genre?.id ?: 0L, "Oracle Query Neverwhere", 405, existing.publisher?.id ?: 0L, lastUpdated).collectList().block()
        def insertedTitle = repository.customInsertReturningTitle(existing.author?.id ?: 0L, existing.genre?.id ?: 0L, "Oracle Query Title", 406, existing.publisher?.id ?: 0L, lastUpdated).block()
        def reloadedInsertedBook = repository.findById(insertedBook.id).block()
        def reloadedInsertedBooks = insertedBooks.collect { repository.findById(it.id).block() }

        then:
        insertedBook.id != null
        insertedBook.title == "Oracle Query Coraline"
        insertedBooks.size() == 1
        insertedBooks[0].id != null
        insertedBooks[0].title == "Oracle Query Neverwhere"
        insertedTitle == "Oracle Query Title"
        reloadedInsertedBook.id == insertedBook.id
        reloadedInsertedBook.title == insertedBook.title
        reloadedInsertedBooks*.id == insertedBooks*.id
        reloadedInsertedBooks*.title == insertedBooks*.title

        when:
        def updated = repository.customUpdateReturning(insertedBook.id, "Oracle Query Coraline Updated", 407, lastUpdated).block()
        def deletedTitle = repository.customDeleteReturningTitle(updated.id).block()

        then:
        updated.id == insertedBook.id
        updated.title == "Oracle Query Coraline Updated"
        deletedTitle == "Oracle Query Coraline Updated"
    }


    void "test collection delete returning"() {
        given:
        def repository = context.getBean(OracleReactiveBookRepository)
        def savedBooks = repository.saveAll([
                new Book(title: "Oracle Delete Returning One", totalPages: 111),
                new Book(title: "Oracle Delete Returning Two", totalPages: 222)
        ]).collectList().block()

        when:
        def deletedBooks = repository.deleteReturning(savedBooks).collectList().block()
        def reloadedBooks = savedBooks.collect { repository.findById(it.id).block() }

        then:
        deletedBooks*.id == savedBooks*.id
        deletedBooks*.title == savedBooks*.title
        reloadedBooks.every { it == null }
    }

    void "test custom update returning title with expanded ids"() {
        given:
        def repository = context.getBean(OracleReactiveBookRepository)
        def book = repository.save(new Book(title: "Oracle Expanded R2DBC", totalPages: 222)).block()

        when:
        def updatedTitle = repository.customUpdateReturningTitleWithExpandedIds(book.id, "Oracle Expanded Updated", [book.id, Long.MAX_VALUE]).block()

        then:
        updatedTitle == "Oracle Expanded Updated"
        repository.findById(book.id).block().title == "Oracle Expanded Updated"
    }

    void "test custom update returning dto projection"() {
        given:
        def repository = context.getBean(OracleReactiveBookRepository)
        def book = repository.save(new Book(title: "Oracle DTO R2DBC", totalPages: 333)).block()

        when:
        BookDto dto = repository.customUpdateReturningDto(book.id, "Oracle DTO Updated", 444).block()

        then:
        dto.title == "Oracle DTO Updated"
        dto.totalPages == 444
        def updated = repository.findById(book.id).block()
        updated.title == "Oracle DTO Updated"
        updated.totalPages == 444
    }

    void "test custom delete returning object array projection"() {
        given:
        def repository = context.getBean(OracleReactiveBookRepository)
        def book = repository.save(new Book(title: "Oracle Array R2DBC", totalPages: 555)).block()

        when:
        Object[] values = repository.customDeleteReturningTitleAndPages(book.id).block()

        then:
        values as List == [book.title, book.totalPages]
        repository.findById(book.id).block() == null
    }

}
