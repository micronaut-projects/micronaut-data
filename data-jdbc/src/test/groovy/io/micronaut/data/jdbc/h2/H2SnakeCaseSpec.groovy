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
package io.micronaut.data.jdbc.h2

import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Author
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class H2SnakeCaseSpec extends Specification {

    @Inject H2BookRepository bookRepository
    @Inject H2AuthorRepository authorRepository

    void "snake_case find_by_title works"() {
        given:
        def author = authorRepository.save(new Author(name: 'A'))
        def b = bookRepository.save(new Book(author: author, title: 'Snake', totalPages: 100))
        expect:
        bookRepository.find_by_title(b.title).id == b.id
        when:
        def book = bookRepository.find_by_author_name(author.name).orElse(null)
        then:
        book
        book.id == b.id
        when:
        def books = bookRepository.query_all()
        then:
        books.size() > 0
        when:
        books = bookRepository.find()
        then:
        books.size() > 0
        cleanup:
        bookRepository.delete_all()
        authorRepository.deleteAll()
    }
}
