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
package io.micronaut.data.r2dbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Insert
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class H2BatchInsertSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    H2BatchInsertBookRepository repository = context.getBean(H2BatchInsertBookRepository)

    void setup() {
        repository.deleteAll()
    }

    void "custom void insertAll stores generated-id inserts without mutating input ids"() {
        given:
        def books = [
            new H2R2dbcBatchInsertBook(title: "Solaris"),
            new H2R2dbcBatchInsertBook(title: "Eden")
        ]

        when:
        repository.customInsertAll(books)
        def savedBooks = repository.findAll()

        then:
        books*.id == [null, null]
        savedBooks.size() == 2
        savedBooks*.id.every { it != null }
        savedBooks*.title as Set == ["Solaris", "Eden"] as Set
    }

    void "custom count insertAll stores generated-id inserts without mutating input ids"() {
        given:
        def books = [
            new H2R2dbcBatchInsertBook(title: "Fiasco"),
            new H2R2dbcBatchInsertBook(title: "The Invincible")
        ]

        when:
        long inserted = repository.customInsertAllCount(books)
        def savedBooks = repository.findAll()

        then:
        inserted == 2
        books*.id == [null, null]
        savedBooks.size() == 2
        savedBooks*.id.every { it != null }
        savedBooks*.title as Set == ["Fiasco", "The Invincible"] as Set
    }
}

@MappedEntity("h2_r2dbc_batch_insert_book")
class H2R2dbcBatchInsertBook {

    @Id
    @GeneratedValue
    Long id

    String title
}

@R2dbcRepository(dialect = Dialect.H2)
interface H2BatchInsertBookRepository extends CrudRepository<H2R2dbcBatchInsertBook, Long> {

    @Insert
    void customInsertAll(List<H2R2dbcBatchInsertBook> entities)

    @Insert
    long customInsertAllCount(List<H2R2dbcBatchInsertBook> entities)
}
