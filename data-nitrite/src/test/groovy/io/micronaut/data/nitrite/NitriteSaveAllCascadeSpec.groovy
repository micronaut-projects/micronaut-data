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
package io.micronaut.data.nitrite

import io.micronaut.data.document.tck.entities.Author
import io.micronaut.data.document.tck.entities.AuthorBooksDto
import io.micronaut.data.document.tck.entities.Book
import io.micronaut.data.document.tck.entities.BookDto
import io.micronaut.data.nitrite.tck.NitriteAuthorRepository
import io.micronaut.data.nitrite.tck.NitriteBookRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteSaveAllCascadeSpec extends Specification {

    @Inject
    NitriteAuthorRepository authorRepository

    @Inject
    NitriteBookRepository bookRepository

    def setup() {
        // Clear all data before each test
        authorRepository.findAll().each { authorRepository.delete(it) }
        bookRepository.findAll().each { bookRepository.delete(it) }
    }

    void "test saveAll cascades to ONE_TO_MANY children"() {
        given:
            def authors = [
                new Author(name: "Stephen King"),
                new Author(name: "James Patterson")
            ]
            authors[0].books = [
                new Book(title: "The Stand", totalPages: 1000),
                new Book(title: "Pet Cemetery", totalPages: 400)
            ] as Set
            authors[1].books = [
                new Book(title: "Along Came a Spider", totalPages: 300),
                new Book(title: "Double Cross", totalPages: 300)
            ] as Set

        when:
            authorRepository.saveAll(authors)

        then:
            def allBooks = bookRepository.findAll().toList()
            allBooks.size() == 4
            allBooks*.title.sort() == ["Along Came a Spider", "Double Cross", "Pet Cemetery", "The Stand"].sort()
    }

    void "test saveAuthorBooks pattern from TCK"() {
        given:
            def authorBooksDtos = [
                new AuthorBooksDto("Stephen King", [
                    new BookDto("The Stand", 1000),
                    new BookDto("Pet Cemetery", 400)
                ]),
                new AuthorBooksDto("James Patterson", [
                    new BookDto("Along Came a Spider", 300),
                    new BookDto("Double Cross", 300)
                ])
            ]

        when:
            bookRepository.saveAuthorBooks(authorBooksDtos)

        then:
            def allBooks = bookRepository.findAll().toList()
            allBooks.size() == 4
            allBooks*.title.sort() == ["Along Came a Spider", "Double Cross", "Pet Cemetery", "The Stand"].sort()
            
            def allAuthors = authorRepository.findAll().toList()
            allAuthors.size() == 2
            allAuthors*.name.sort() == ["James Patterson", "Stephen King"].sort()
    }
}
