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
package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.tests.AbstractQuerySpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject

@MicronautTest(packages = "io.micronaut.data.tck.entities", rollback = false, transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class HibernateQuerySpec extends AbstractQuerySpec {

    @Shared
    @Inject
    BookRepository br

    @Shared
    @Inject
    AuthorRepository ar

    void "test @Where annotation placehoder"() {
        given:
        def size = bookRepository.countNativeByTitleWithPagesGreaterThan("The%", 300)
        def books = bookRepository.findByTitleStartsWith("The", 300)

        expect:
        books.size() == size
    }

    void "test native query"() {
        given:
        def books = bookRepository.listNativeBooks("The%")

        expect:
        books.size() == 3
        books.every({ it instanceof Book })
    }

    void "test join on many ended association"() {
        when:
        def author = authorRepository.searchByName("Stephen King")

        then:
        author != null
        author.books.size() == 2
    }

    @Override
    BookRepository getBookRepository() {
        return br
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return ar
    }
}
