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
package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import jakarta.inject.Singleton
import oracle.jdbc.OracleConnection
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class OracleQueryNotificationSpec extends Specification implements OracleTestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext context

    @Shared
    QueryNotificationBookRepository repository

    @Shared
    QueryNotificationBookListener listener

    @Override
    List<String> packages() {
        return Arrays.asList(getClass().package.name)
    }

    def setupSpec() {
        // Test Resources creates the regular test user without this Oracle-specific privilege.
        // Bootstrap once as SYSTEM to grant it, then run the actual listener as the test user.
        def administratorProperties = properties + [
            "datasources.default.username": "system",
            "datasources.default.password": "test",
            "datasources.default.schema-generate": "NONE"
        ]
        ApplicationContext administratorContext = ApplicationContext.run(administratorProperties)
        try {
            administratorContext.getBean(DefaultJdbcRepositoryOperations).execute { connection ->
                connection.createStatement().withCloseable { statement ->
                    statement.execute("GRANT CHANGE NOTIFICATION TO test")
                }
                true
            }
        } finally {
            administratorContext.close()
        }
        context = ApplicationContext.run(properties + ["query-notification.enabled": "true"])
        repository = context.getBean(QueryNotificationBookRepository)
        listener = context.getBean(QueryNotificationBookListener)
    }

    void "change listener receives an entity after an Oracle row is inserted"() {
        when:
        def saved = repository.save(new QueryNotificationBook(title: "Continuous Query Notification"))
        def notification = listener.poll()

        then:
        notification
        notification.id == saved.id
        notification.title == "Continuous Query Notification"
    }

    void "change listener receives every entity affected by a bulk update"() {
        given:
        def firstBook = new QueryNotificationBook(title: "First book")
        def secondBook = new QueryNotificationBook(title: "Second book")
        def thirdBook = new QueryNotificationBook(title: "Third book")

        when:
        repository.saveAll([firstBook, secondBook, thirdBook])
        def insertNotifications = [listener.poll(), listener.poll(), listener.poll()]

        then:
        insertNotifications.every { it }
        (insertNotifications*.id as Set) == ([firstBook.id, secondBook.id, thirdBook.id] as Set)
        (insertNotifications*.title as Set) == (["First book", "Second book", "Third book"] as Set)

        when:
        def updated = repository.updateTitleByIds("Bulk updated", [firstBook.id, secondBook.id])
        def updateNotifications = [listener.poll(), listener.poll()]

        then:
        updated == 2
        updateNotifications.every { it }
        (updateNotifications*.id as Set) == ([firstBook.id, secondBook.id] as Set)
        (updateNotifications*.title as Set) == (["Bulk updated"] as Set)
    }
}

@MappedEntity("query_notification_book")
class QueryNotificationBook {
    @Id
    @GeneratedValue
    Long id

    String title
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface QueryNotificationBookRepository extends CrudRepository<QueryNotificationBook, Long> {
    @Query("UPDATE query_notification_book SET title = :title WHERE id IN (:ids)")
    long updateTitleByIds(String title, List<Long> ids)
}

@Singleton
@Requires(property = "query-notification.enabled")
class QueryNotificationBookListener {
    private final LinkedBlockingQueue<QueryNotificationBook> notifications = new LinkedBlockingQueue<>()

    @ChangeListener(properties = [
        @ChangeListener.Property(name = OracleConnection.DCN_CLIENT_INIT_CONNECTION, value = "true")
    ])
    void onBookChanged(QueryNotificationBook book) {
        notifications.offer(book)
    }

    QueryNotificationBook poll() {
        notifications.poll(10, TimeUnit.SECONDS)
    }
}
