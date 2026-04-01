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
package io.micronaut.data.r2dbc.postgres

import groovy.transform.Memoized
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.repositories.BookReactiveRepository
import io.micronaut.data.tck.repositories.PersonReactiveRepository
import io.micronaut.data.tck.repositories.StudentReactiveRepository
import io.micronaut.data.tck.tests.AbstractReactiveRepositorySpec

import java.time.LocalDateTime

class PostgresReactiveRepositorySpec extends AbstractReactiveRepositorySpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    PersonReactiveRepository getPersonRepository() {
        return context.getBean(PostgresPersonReactiveRepository)
    }

    @Memoized
    @Override
    StudentReactiveRepository getStudentRepository() {
        return context.getBean(PostgresStudentReactiveRepository)
    }

    @Memoized
    @Override
    BookReactiveRepository getBookRepository() {
        return context.getBean(PostgresReactiveBookRepository)
    }

    void "test reactive-only returning contract"() {
        given:
        def repository = context.getBean(PostgresReactiveReturningBookRepository)
        def bookRepository = context.getBean(PostgresReactiveBookRepository)
        def authorRepository = context.getBean(PostgresAuthorRepository)
        studentRepository.deleteAll().blockingGet()
        def author = authorRepository.customInsertReturningAuthor("Reactive Stephen King", null)
        def replacementAuthor = authorRepository.customInsertReturningAuthor("Reactive Neil Gaiman", null)
        def lastUpdated = LocalDateTime.now()
        def singleBook = new Book(title: "Reactive Derived It", totalPages: 401, author: author, lastUpdated: lastUpdated)
        def booksToSave = [
                new Book(title: "Reactive Derived Stand", totalPages: 402, author: author, lastUpdated: lastUpdated),
                new Book(title: "Reactive Derived Shining", totalPages: 403, author: author, lastUpdated: lastUpdated)
        ]

        when:
        def savedBook = repository.saveReturning(singleBook).block()
        def savedBooks = repository.saveReturningMany(booksToSave).collectList().block()
        def reloadedSingle = bookRepository.findByTitle("Reactive Derived It")
        def reloadedMany = [
                bookRepository.findByTitle("Reactive Derived Stand"),
                bookRepository.findByTitle("Reactive Derived Shining")
        ]

        then:
        savedBook.id != null
        savedBook.title == "Reactive Derived It"
        savedBooks*.id.every { it != null }
        savedBooks*.id.toSet().size() == savedBooks.size()
        savedBooks*.title == ["Reactive Derived Stand", "Reactive Derived Shining"]
        reloadedSingle.id == savedBook.id
        reloadedSingle.title == savedBook.title
        reloadedMany.size() == 2

        when:
        def insertedBook = repository.insertReturningBook(author.id, null, "Reactive Query Coraline", 404, null, lastUpdated).block()
        def insertedBooks = repository.insertReturningBooks(author.id, null, "Reactive Query Neverwhere", 405, null, lastUpdated).collectList().block()
        def reloadedInsertedBook = bookRepository.findByTitle("Reactive Query Coraline")
        def reloadedInsertedBooks = [
                bookRepository.findByTitle("Reactive Query Neverwhere")
        ]

        then:
        insertedBook.id != null
        insertedBook.title == "Reactive Query Coraline"
        insertedBooks.size() == 1
        insertedBooks[0].id != null
        insertedBooks[0].title == "Reactive Query Neverwhere"
        reloadedInsertedBook.id == insertedBook.id
        reloadedInsertedBook.title == insertedBook.title
        reloadedInsertedBooks*.id == insertedBooks*.id
        reloadedInsertedBooks*.title == insertedBooks*.title

        when:
        savedBook.title = "Reactive Derived It Updated"
        def updatedBook = repository.updateReturning(savedBook).block()

        then:
        updatedBook.id == savedBook.id
        updatedBook.title == "Reactive Derived It Updated"

        when:
        def updatedByQuery = repository.customUpdateReturning(replacementAuthor.id, [savedBook.id] + savedBooks*.id).collectList().block()

        then:
        updatedByQuery*.id.toSet() == ([savedBook.id] + savedBooks*.id).toSet()
        updatedByQuery*.author*.id.every { it == replacementAuthor.id }

        when:
        def deletedBook = repository.deleteReturning(updatedBook).block()
        def deletedBooks = repository.deleteReturning(savedBooks).collectList().block()

        then:
        deletedBook.id == savedBook.id
        deletedBook.title == "Reactive Derived It Updated"
        deletedBooks*.id == savedBooks*.id
        deletedBooks*.title == savedBooks*.title
    }

    void "test returning insert update and delete"() {
        given:
        def repository = context.getBean(PostgresReactiveBookRepository)
        def authorRepository = context.getBean(PostgresAuthorRepository)
        studentRepository.deleteAll().blockingGet()
        def author = authorRepository.customInsertReturningAuthor("Stephen King", null)
        def existing = repository.customInsertReturningBook(author.id, null, "Pet Cemetery", 321, null, LocalDateTime.now())
        def authorId = existing.author.id
        def lastUpdated = LocalDateTime.now()
        def derivedBook = new Book(title: "Derived It", totalPages: 301, author: author, lastUpdated: lastUpdated)
        def derivedBooksToSave = [
                new Book(title: "Derived Stand", totalPages: 101, author: author, lastUpdated: lastUpdated),
                new Book(title: "Derived Shining", totalPages: 201, author: author, lastUpdated: lastUpdated)
        ]

        when:
        def derivedSavedBook = repository.saveReturning(derivedBook)
        def derivedSavedBooks = repository.saveReturningAll (derivedBooksToSave)
        def savedBook = repository.customInsertReturningBook(authorId, null, "It", 300, null, lastUpdated)
        def savedBooks = repository.customInsertReturningBooks(authorId, null, "The Stand", 100, null, lastUpdated)
        def reloadedDerivedBook = repository.findByTitle("Derived It")
        def reloadedDerivedSavedBooks = [
                repository.findByTitle("Derived Stand"),
                repository.findByTitle("Derived Shining")
        ]

        then:
        derivedSavedBook.id != null
        derivedSavedBook.title == "Derived It"
        derivedSavedBooks*.id.every { it != null }
        derivedSavedBooks*.id.toSet().size() == derivedSavedBooks.size()
        derivedSavedBooks*.title == ["Derived Stand", "Derived Shining"]
        reloadedDerivedBook.id == derivedSavedBook.id
        reloadedDerivedBook.title == derivedSavedBook.title
        reloadedDerivedSavedBooks*.id == derivedSavedBooks*.id
        reloadedDerivedSavedBooks*.title == derivedSavedBooks*.title
        savedBook.id != null
        savedBook.title == "It"
        savedBooks.size() == 1
        savedBooks[0].id != null
        savedBooks[0].title == "The Stand"

        when:
        savedBook.title = "It Updated"
        def updatedBook = repository.updateReturning(savedBook)
        savedBooks[0].title = "The Stand Updated"
        def updatedBooks = repository.updateReturning(savedBooks)
        def updatedTitleFromEntity = repository.updateReturningTitle(updatedBook)
        def updatedTitleById = repository.updateReturningTitle(updatedBook.id, "It Final")
        def authorUpdatedBooks = repository.updateReturning(updatedBook.author.id)
        def modifiedBook = repository.modifyReturning(updatedBook.author.id)
        def customUpdatedBooks = repository.customUpdateReturningBooks(updatedBook.author.id)
        def customUpdatedBook = repository.customUpdateReturningBook(updatedBook.author.id)
        def reloadedUpdatedBook = repository.findByTitle("It Final")

        then:
        updatedBook.id == savedBook.id
        updatedBook.title == "It Updated"
        updatedBooks*.id == savedBooks*.id
        updatedBooks*.title == ["The Stand Updated"]
        updatedTitleFromEntity == "It Updated"
        updatedTitleById == "It Final"
        authorUpdatedBooks*.author*.id.every { it == updatedBook.author.id }
        modifiedBook.author.id == updatedBook.author.id
        customUpdatedBooks*.author*.id.every { it == updatedBook.author.id }
        customUpdatedBook.author.id == updatedBook.author.id
        reloadedUpdatedBook.id == updatedBook.id
        reloadedUpdatedBook.title == "It Final"

        when:
        def deletedTitle = repository.deleteReturningTitle(savedBooks[0])
        def extraBook = repository.customInsertReturningBook(authorId, null, "Delete Me", 111, null, lastUpdated)
        def deletedLastUpdated = repository.deleteReturningLastUpdated(extraBook.id, extraBook.title)
        def extraBook2 = repository.customInsertReturningBook(authorId, null, "Delete Me Too", 112, null, lastUpdated)
        def deletedLastUpdatedByIdAndTitle = repository.deleteByIdAndTitleReturningLastUpdated(extraBook2.id, extraBook2.title)
        def finalUpdatedBook = repository.findByTitle("It Final")
        def deletedBook = repository.deleteReturning(finalUpdatedBook)
        def customDeleteBook = repository.customInsertReturningBook(authorId, null, "Delete Custom", 113, null, lastUpdated)
        def customDeletedBook = repository.customDeleteOne(customDeleteBook.id)

        then:
        deletedTitle == "The Stand Updated"
        deletedLastUpdated != null
        deletedLastUpdatedByIdAndTitle != null
        deletedBook.title == "It Final"
        customDeletedBook.title == "Delete Custom"

        when:
        def moreBooks = [
                repository.customInsertReturningBook(authorId, null, "Book A", 10, null, lastUpdated),
                repository.customInsertReturningBook(authorId, null, "Book B", 20, null, lastUpdated),
                repository.customInsertReturningBook(authorId, null, "Book C", 30, null, lastUpdated)
        ]
        def deletedByAuthor = repository.deleteReturning(moreBooks[0].author.id)
        def reSavedBooks = [
                repository.customInsertReturningBook(authorId, null, "Book D", 40, null, lastUpdated),
                repository.customInsertReturningBook(authorId, null, "Book E", 50, null, lastUpdated)
        ]
        def deletedBooks = repository.deleteReturning(reSavedBooks)
        repository.customInsertReturningBook(authorId, null, "Book F", 60, null, lastUpdated)
        repository.customInsertReturningBook(authorId, null, "Book G", 70, null, lastUpdated)
        def deletedAllBooks = repository.customDeleteAll()

        then:
        deletedByAuthor*.title.containsAll(["Book A", "Book B", "Book C"])
        deletedBooks*.title == ["Book D", "Book E"]
        deletedAllBooks*.title.containsAll(["Book F", "Book G"])
    }


}
