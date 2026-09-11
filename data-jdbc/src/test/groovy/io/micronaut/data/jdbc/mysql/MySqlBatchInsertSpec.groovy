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
package io.micronaut.data.jdbc.mysql

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MySqlBatchInsertSpec extends Specification implements MySQLTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    MySqlBatchRecordRepository repository = context.getBean(MySqlBatchRecordRepository)

    @Shared
    MySqlBatchCascadeParentRepository cascadeParentRepository = context.getBean(MySqlBatchCascadeParentRepository)

    @Shared
    MySqlBatchCascadeChildRepository cascadeChildRepository = context.getBean(MySqlBatchCascadeChildRepository)

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
        cascadeChildRepository.deleteAll()
        cascadeParentRepository.deleteAll()
        queryLogAppender.list.clear()
    }

    void "saveAll generated-id record inserts batch and populate ids"() {
        given:
        def records = (0..<100).collect { new MySqlBatchRecord(null, "name-$it") }

        when:
        List<MySqlBatchRecord> saved = repository.saveAll(records).toList()

        then:
        saved.size() == 100
        saved.collect { it.id() }.every { it != null && it != 0L }
        insertQueryExecutions("mysql_batch_record") == 1
    }

    void "cascaded generated-id child inserts batch"() {
        given:
        def parent = new MySqlBatchCascadeParent()
        parent.children = (0..<100).collect { new MySqlBatchCascadeChild(name: "name-$it", parent: parent) }

        when:
        cascadeParentRepository.save(parent)

        then:
        parent.id != null
        parent.children*.id.every { it != null }
        cascadeChildRepository.count() == 100
        insertQueryExecutions("mysql_batch_cascade_child") == 1
    }

    void "custom void insertAll batches generated-id record inserts"() {
        given:
        def records = (0..<100).collect { new MySqlBatchRecord(null, "name-$it") }

        when:
        repository.insertAll(records)
        def savedRecords = repository.findAll()

        then:
        savedRecords.size() == 100
        savedRecords.every { it.id() != null && it.id() != 0L }
        insertQueryExecutions("mysql_batch_record") == 1
    }

    private long insertQueryExecutions(String tableName) {
        queryLogAppender.list.count { event ->
            String message = event.formattedMessage
            message.contains("Executing SQL query: INSERT INTO")
                && message.contains("`${tableName}`")
        }
    }
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlBatchRecordRepository extends CrudRepository<MySqlBatchRecord, Long> {

    void insertAll(List<MySqlBatchRecord> entities)
}

@MappedEntity("mysql_batch_cascade_parent")
class MySqlBatchCascadeParent {

    @Id
    @GeneratedValue
    Long id

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = Relation.Cascade.PERSIST)
    List<MySqlBatchCascadeChild> children
}

@MappedEntity("mysql_batch_cascade_child")
class MySqlBatchCascadeChild {

    @Id
    @GeneratedValue
    Long id

    String name

    @Relation(Relation.Kind.MANY_TO_ONE)
    MySqlBatchCascadeParent parent
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlBatchCascadeParentRepository extends CrudRepository<MySqlBatchCascadeParent, Long> {
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlBatchCascadeChildRepository extends CrudRepository<MySqlBatchCascadeChild, Long> {
}
