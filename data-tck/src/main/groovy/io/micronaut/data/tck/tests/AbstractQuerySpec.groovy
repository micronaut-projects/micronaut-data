/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookRepository
import spock.lang.Specification

abstract class AbstractQuerySpec extends Specification {
    abstract BookRepository getBookRepository()
    abstract AuthorRepository getAuthorRepository()

    void init() {
    }

    def setupSpec() {
        bookRepository.deleteAll()
        init()
        bookRepository.save(new Book(title: "Anonymous", totalPages: 400))
        // blank title
        bookRepository.save(new Book(title: "", totalPages: 0))
        // book without an author
        bookRepository.setupData()
    }

    void "test is null or empty"() {
        expect:
        bookRepository.count() == 8
        bookRepository.findByAuthorIsNull().size() == 2
        bookRepository.findByAuthorIsNotNull().size() == 6
        bookRepository.countByTitleIsEmpty() == 1
        bookRepository.countByTitleIsNotEmpty() == 7
    }


    void "test string comparison methods"() {
        expect:
        authorRepository.countByNameContains("e") == 2
        authorRepository.findByNameStartsWith("S").name == "Stephen King"
        authorRepository.findByNameEndsWith("w").name == "Don Winslow"
        authorRepository.findByNameIgnoreCase("don winslow").name == "Don Winslow"
    }
}
