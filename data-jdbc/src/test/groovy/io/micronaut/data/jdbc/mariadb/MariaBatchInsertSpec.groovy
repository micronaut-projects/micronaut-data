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
package io.micronaut.data.jdbc.mariadb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Insert
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MariaBatchInsertSpec extends Specification implements MariaTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    MariaBatchBookRepository repository = context.getBean(MariaBatchBookRepository)

    @Shared
    MariaBatchRecordRepository recordRepository = context.getBean(MariaBatchRecordRepository)

    void setup() {
        repository.deleteAll()
        recordRepository.deleteAll()
    }

    void "custom void insertAll stores generated-id inserts without mutating input ids"() {
        given:
        def books = [
            new MariaBatchBook(title: "The Left Hand"),
            new MariaBatchBook(title: "The Dispossessed")
        ]

        when:
        repository.customInsertAll(books)
        def savedBooks = repository.findAll()

        then:
        savedBooks.size() == 2
        books*.id == [null, null]
        savedBooks*.id.every { it != null }
        savedBooks*.title as Set == ["The Left Hand", "The Dispossessed"] as Set
    }

    void "custom count insertAll stores generated-id inserts without mutating input ids"() {
        given:
        def books = [
            new MariaBatchBook(title: "The Lathe of Heaven"),
            new MariaBatchBook(title: "City of Illusions")
        ]

        when:
        long inserted = repository.customInsertAllCount(books)
        def savedBooks = repository.findAll()

        then:
        inserted == 2
        savedBooks.size() == 2
        books*.id == [null, null]
        savedBooks*.id.every { it != null }
        savedBooks*.title as Set == ["The Lathe of Heaven", "City of Illusions"] as Set
    }

    void "saveAll generated-key inserts populate ids through fallback path"() {
        given:
        def books = [
            new MariaBatchBook(title: "A Wizard of Earthsea"),
            new MariaBatchBook(title: "The Tombs of Atuan")
        ]

        when:
        def saved = repository.saveAll(books)

        then:
        saved*.id.every { it != null }
        repository.count() == 2
    }

    void "saveAll generated-key record inserts populate ids through fallback path"() {
        given:
        def records = (0..<100).collect { new MariaBatchRecord(0L, "name-$it") }

        when:
        List<MariaBatchRecord> saved = recordRepository.saveAll(records)

        then:
        saved.size() == 100
        saved.collect { it.id() }.every { it != null && it != 0L }
        records.collect { it.id() }.every { it == 0L }
    }

    void "custom void insertAll stores generated-id record inserts without mutating input ids"() {
        given:
        def records = (0..<100).collect { new MariaBatchRecord(0L, "name-$it") }

        when:
        recordRepository.insertAll(records)
        def savedRecords = recordRepository.findAll()

        then:
        records.collect { it.id() }.every { it == 0L }
        savedRecords.size() == 100
        savedRecords.every { it.id() != null && it.id() != 0L }
    }
}

@MappedEntity("maria_batch_book")
class MariaBatchBook {

    @Id
    @GeneratedValue
    Long id

    String title
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MariaBatchBookRepository extends CrudRepository<MariaBatchBook, Long> {

    @Insert
    void customInsertAll(List<MariaBatchBook> entities)

    @Insert
    long customInsertAllCount(List<MariaBatchBook> entities)
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MariaBatchRecordRepository extends CrudRepository<MariaBatchRecord, Long> {

    void insertAll(List<MariaBatchRecord> entities)
}
