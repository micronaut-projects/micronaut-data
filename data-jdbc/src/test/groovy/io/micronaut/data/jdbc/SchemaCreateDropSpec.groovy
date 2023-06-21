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
package io.micronaut.data.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.repositories.BookRepository
import spock.lang.AutoCleanup
import spock.lang.Specification

abstract class SchemaCreateDropSpec extends Specification {

    abstract BookRepository getBookRepository()

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(properties)

    void "book is created"() {
        given:
        bookRepository.save(new Book(title: "title"))

        expect:
        bookRepository.count() == 1
    }

    void "book was dropped"() {
        expect:
        bookRepository.count() == 0
    }
}
