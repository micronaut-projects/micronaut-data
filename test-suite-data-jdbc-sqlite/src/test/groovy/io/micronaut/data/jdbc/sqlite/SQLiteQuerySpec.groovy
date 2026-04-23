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
package io.micronaut.data.jdbc.sqlite

import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.tests.AbstractQuerySpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared

import jakarta.inject.Inject

@MicronautTest
@SQLiteDBProperties
class SQLiteQuerySpec extends AbstractQuerySpec {
    @Shared
    @Inject
    SQLiteBookRepository br
    @Shared
    @Inject
    SQLiteAuthorRepository ar

    @Override
    SQLiteBookRepository getBookRepository() {
        return br
    }

    @Override
    SQLiteAuthorRepository getAuthorRepository() {
        return ar
    }

    void "test @Where annotation placehoder"() {
        given:
        def size = bookRepository.countNativeByTitleWithPagesGreaterThan("The%", 300)
        def books = bookRepository.findByTitleStartsWith("The", 300)

        expect:
        books.size() == size
    }

    void "test explicit @Query update methods"() {
        when:
        def r = br.setPages(800, "The Border")

        then:
        br.findByTitle("The Border").totalPages == 800
        r == 1

        when:
        def king = ar.findByName("Stephen King")
        br.save(new Book(author: king, title: "Whatever", totalPages: 200))

        then:
        br.findByTitle("Whatever") != null

        when:
        r = br.wipeOutBook("Whatever")

        then:
        r == 1

        when:
        br.findByTitle("Whatever")

        then:
        thrown(EmptyResultException)
    }
}
