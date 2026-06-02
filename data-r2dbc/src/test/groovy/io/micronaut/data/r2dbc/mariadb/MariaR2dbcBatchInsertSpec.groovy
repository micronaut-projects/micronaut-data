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
package io.micronaut.data.r2dbc.mariadb

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MariaR2dbcBatchInsertSpec extends Specification implements MariaDbTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    MariaR2dbcBatchRecordRepository repository = context.getBean(MariaR2dbcBatchRecordRepository)

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
        queryLogAppender.list.clear()
    }

    void "saveAll falls back to generated-key record inserts and populates ids"() {
        given:
        def records = (0..<100).collect { new MariaR2dbcBatchRecord(0L, "name-$it") }

        when:
        List<MariaR2dbcBatchRecord> saved = repository.saveAll(records)

        then:
        saved.size() == 100
        saved.collect { it.id() }.every { it != null && it != 0L }
        records.collect { it.id() }.every { it == 0L }
        insertQueryExecutions("maria_r2dbc_batch_record") == 100
    }

    void "custom void insertAll batches generated-id record inserts without mutating input ids"() {
        given:
        def records = (0..<100).collect { new MariaR2dbcBatchRecord(0L, "name-$it") }

        when:
        repository.insertAll(records)
        def savedRecords = repository.findAll()

        then:
        records.collect { it.id() }.every { it == 0L }
        savedRecords.size() == 100
        savedRecords.every { it.id() != null && it.id() != 0L }
        insertQueryExecutions("maria_r2dbc_batch_record") == 1
    }

    private long insertQueryExecutions(String tableName) {
        queryLogAppender.list.count { event ->
            String message = event.formattedMessage
            message.contains("Executing SQL query: INSERT INTO")
                && message.contains("`${tableName}`")
        }
    }
}

@R2dbcRepository(dialect = Dialect.MYSQL)
interface MariaR2dbcBatchRecordRepository extends CrudRepository<MariaR2dbcBatchRecord, Long> {

    void insertAll(List<MariaR2dbcBatchRecord> entities)
}
