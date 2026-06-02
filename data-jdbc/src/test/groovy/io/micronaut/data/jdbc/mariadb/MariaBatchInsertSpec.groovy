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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Insert
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import org.slf4j.LoggerFactory
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

    @Shared
    Logger queryLogger = LoggerFactory.getLogger("io.micronaut.data.query") as Logger

    @Shared
    Level previousQueryLogLevel

    @Shared
    ListAppender<ILoggingEvent> queryLogAppender = new ListAppender<>()

    void setupSpec() {
        previousQueryLogLevel = queryLogger.level
        queryLogger.level = Level.DEBUG
        queryLogAppender.start()
        queryLogger.addAppender(queryLogAppender)
    }

    void cleanupSpec() {
        queryLogger.detachAppender(queryLogAppender)
        queryLogger.level = previousQueryLogLevel
        queryLogAppender.stop()
    }

    void setup() {
        repository.deleteAll()
        recordRepository.deleteAll()
        queryLogAppender.list.clear()
    }

    void "custom void insertAll batches generated-id inserts without mutating input ids"() {
        given:
        def books = [
            new MariaBatchBook(title: "The Left Hand"),
            new MariaBatchBook(title: "The Dispossessed")
        ]

        when:
        repository.customInsertAll(books)

        then:
        repository.count() == 2
        books*.id == [null, null]
        repository.findAll()*.id.every { it != null }
        repository.findAll()*.title as Set == ["The Left Hand", "The Dispossessed"] as Set
    }

    void "custom count insertAll batches generated-id inserts without mutating input ids"() {
        given:
        def books = [
            new MariaBatchBook(title: "The Lathe of Heaven"),
            new MariaBatchBook(title: "City of Illusions")
        ]

        when:
        long inserted = repository.customInsertAllCount(books)

        then:
        inserted == 2
        repository.count() == 2
        books*.id == [null, null]
        repository.findAll()*.id.every { it != null }
        repository.findAll()*.title as Set == ["The Lathe of Heaven", "City of Illusions"] as Set
    }

    void "saveAll stays on the generated-key path for generated identities"() {
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

    void "saveAll batches generated-id record inserts and populates ids"() {
        given:
        def records = (0..<100).collect { new MariaBatchRecord(0L, "name-$it") }

        when:
        List<MariaBatchRecord> saved = recordRepository.saveAll(records)

        then:
        saved.size() == 100
        saved.collect { it.id() }.every { it != null && it != 0L }
        records.collect { it.id() }.every { it == 0L }
        insertQueryExecutions("maria_batch_record") == 1
    }

    void "custom void insertAll batches generated-id record inserts without mutating input ids"() {
        given:
        def records = (0..<100).collect { new MariaBatchRecord(0L, "name-$it") }

        when:
        recordRepository.insertAll(records)

        then:
        records.collect { it.id() }.every { it == 0L }
        recordRepository.count() == 100
        recordRepository.findAll().every { it.id() != null && it.id() != 0L }
        insertQueryExecutions("maria_batch_record") == 1
    }

    void "save one by one does not batch generated-id record inserts"() {
        given:
        def records = (0..<100).collect { new MariaBatchRecord(0L, "name-$it") }

        when:
        List<MariaBatchRecord> saved = records.collect { recordRepository.save(it) }

        then:
        saved.size() == 100
        saved.collect { it.id() }.every { it != null && it != 0L }
        records.collect { it.id() }.every { it == 0L }
        insertQueryExecutions("maria_batch_record") == 100
    }

    private long insertQueryExecutions(String tableName) {
        queryLogAppender.list.count { event ->
            String message = event.formattedMessage
            message.contains("Executing SQL query: INSERT INTO")
                && message.contains("`${tableName}`")
        }
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
