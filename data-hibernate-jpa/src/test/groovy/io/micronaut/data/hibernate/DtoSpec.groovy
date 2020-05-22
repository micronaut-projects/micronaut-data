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
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.test.annotation.MicronautTest
import org.hibernate.Hibernate
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class DtoSpec extends Specification {

    @Inject
    @Shared
    BookRepository bookRepository
    @Inject
    @Shared
    BookDtoRepository bookDtoRepository

    void setupSpec() {
        bookRepository.setupData()
    }

    void "test entity graph"() {
        when:
        def results = bookRepository.findAllByTitleStartsWith("The")

        then:
        results.size() == 3
        results.every({ Book b -> Hibernate.isInitialized(b.author)})
    }

    void "test no entity graph"() {
        when:
        def results = bookRepository.findAllByTitleStartingWith("The")
        println "GOT RESULTS"

        then:
        results.every({ Book b -> !Hibernate.isInitialized(b.author)})
        results.size() == 3
    }

    void "test dto projection"() {
        when:
        def results = bookDtoRepository.findByTitleLike("The%")

        then:
        results.size() == 3
        results.every { it instanceof BookDto }
        results.every { it.title.startsWith("The")}
        bookDtoRepository.findOneByTitle("The Stand").title == "The Stand"

        when:"paged result check"
        def result = bookDtoRepository.searchByTitleLike("The%", Pageable.from(0))

        then:"the result is correct"
        result.totalSize == 3
        result.size == 10
        result.content.every { it instanceof BookDto }
        result.content.every { it.title.startsWith("The")}

        when:"Stream is used"
        def dto = bookDtoRepository.findStream("The Stand").findFirst().get()

        then:"The result is correct"
        dto instanceof BookDto
        dto.title == "The Stand"
    }

}
