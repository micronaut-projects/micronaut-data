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


import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Page
import io.micronaut.data.tck.entities.Shelf
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@H2DBProperties
class H2UnidirectionalToManyJoinSpec extends Specification {

    @Inject
    H2ShelfRepository shelfRepository
    @Inject
    H2BookRepository bookRepository
    @Inject
    H2PageRepository pageRepository
    @Inject
    H2ShelfBookRepository shelfBookRepository
    @Inject
    H2BookPageRepository bookPageRepository

    void "test unidirectional join"() {
        given:
        bookRepository.deleteAll()
        Shelf shelf = new Shelf(shelfName: "Some Shelf")
        def b1 = new Book(title: "The Stand", totalPages: 1000)
        b1.pages.add(new Page(num: 10))
        b1.pages.add(new Page(num: 20))
        def b2 = new Book(title: "The Shining", totalPages: 600)
        shelf.books.add(b1)
        shelf.books.add(b2)

        when:
        shelf = shelfRepository.save(shelf)

        then:
        b1.pages.every { it.id != null }
        shelf.books.every { it.id != null }

        when:
        shelf = shelfRepository.findById(shelf.id).orElse(null)

        then:
        shelf != null
        shelf.shelfName == 'Some Shelf'
        // left join causes single result since each
        // book only has a single page
        !shelf.books.isEmpty()
    }

}

