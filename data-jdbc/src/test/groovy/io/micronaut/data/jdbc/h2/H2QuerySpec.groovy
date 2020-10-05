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

import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.tests.AbstractQuerySpec
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject

@MicronautTest
@H2DBProperties
class H2QuerySpec extends AbstractQuerySpec {
    @Shared
    @Inject
    H2BookRepository br
    @Shared
    @Inject
    H2AuthorRepository ar

    @Override
    H2BookRepository getBookRepository() {
        return br
    }

    @Override
    H2AuthorRepository getAuthorRepository() {
        return ar
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
