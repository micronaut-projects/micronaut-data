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
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleQueryNotificationSpec extends Specification implements OracleTestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext context

    @Shared
    ObjectChangeNotificationBookRepository objectChangeRepository

    @Shared
    QueryChangeNotificationBookRepository queryChangeRepository

    @Shared
    ObjectChangeNotificationBookListener objectChangeListener

    @Shared
    QueryChangeNotificationBookListener queryChangeListener

    @Override
    List<String> packages() {
        return Arrays.asList(getClass().package.name)
    }

    def setupSpec() {
        grantChangeNotificationPrivilege()
        context = ApplicationContext.run(properties + ["query-notification.enabled": "true"])
        objectChangeRepository = context.getBean(ObjectChangeNotificationBookRepository)
        queryChangeRepository = context.getBean(QueryChangeNotificationBookRepository)
        objectChangeListener = context.getBean(ObjectChangeNotificationBookListener)
        queryChangeListener = context.getBean(QueryChangeNotificationBookListener)
    }

    void cleanup() {
        objectChangeRepository.deleteAll()
        queryChangeRepository.deleteAll()
    }

    private void grantChangeNotificationPrivilege() {
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
    }

    void "change listener receives an entity after an Oracle row is inserted"() {
        when:
        def saved = objectChangeRepository.save(new ObjectChangeNotificationBook(title: "Continuous Query Notification"))
        def notification = objectChangeListener.poll(ChangeOperation.INSERT)
        def entity = notification?.entity()?.orElse(null)

        then:
        notification
        notification.operation() == ChangeOperation.INSERT
        entity.id == saved.id
        entity.title == "Continuous Query Notification"
        notification.metadata(OracleChangeEventMetadata).orElseThrow().rowId()
    }

    void "change listener receives every entity affected by a bulk update"() {
        given:
        def firstBook = new ObjectChangeNotificationBook(title: "First book")
        def secondBook = new ObjectChangeNotificationBook(title: "Second book")
        def thirdBook = new ObjectChangeNotificationBook(title: "Third book")

        when:
        objectChangeRepository.saveAll([firstBook, secondBook, thirdBook])
        def insertNotifications = [
            objectChangeListener.poll(ChangeOperation.INSERT),
            objectChangeListener.poll(ChangeOperation.INSERT),
            objectChangeListener.poll(ChangeOperation.INSERT)
        ]
        def insertedBooks = insertNotifications*.entity()*.orElseThrow()

        then:
        insertNotifications.every { it }
        insertNotifications.every { it.operation() == ChangeOperation.INSERT }
        (insertedBooks*.id as Set) == ([firstBook.id, secondBook.id, thirdBook.id] as Set)
        (insertedBooks*.title as Set) == (["First book", "Second book", "Third book"] as Set)
        insertNotifications.every { it.metadata(OracleChangeEventMetadata).orElseThrow().rowId() }

        when:
        def updated = objectChangeRepository.updateTitleByIds("Bulk updated", [firstBook.id, secondBook.id])
        def updateNotifications = [
            objectChangeListener.poll(ChangeOperation.UPDATE),
            objectChangeListener.poll(ChangeOperation.UPDATE)
        ]
        def updatedBooks = updateNotifications*.entity()*.orElseThrow()

        then:
        updated == 2
        updateNotifications.every { it }
        updateNotifications.every { it.operation() == ChangeOperation.UPDATE }
        (updatedBooks*.id as Set) == ([firstBook.id, secondBook.id] as Set)
        (updatedBooks*.title as Set) == (["Bulk updated"] as Set)
        updateNotifications.every { it.metadata(OracleChangeEventMetadata).orElseThrow().rowId() }
    }

    void "change listener receives operation and ROWID after an Oracle row is deleted"() {
        given:
        def saved = objectChangeRepository.save(new ObjectChangeNotificationBook(title: "Deleted book"))
        assert objectChangeListener.poll(ChangeOperation.INSERT)

        when:
        objectChangeRepository.deleteById(saved.id)
        def notification = objectChangeListener.poll(ChangeOperation.DELETE)

        then:
        notification
        notification.operation() == ChangeOperation.DELETE
        notification.entity().isEmpty()
        notification.metadata(OracleChangeEventMetadata).orElseThrow().rowId()
    }

    void "query change listener receives an entity after an Oracle row is inserted"() {
        when:
        queryChangeRepository.save(new QueryChangeNotificationBook(title: "Ignored by query notification"))
        def saved = queryChangeRepository.save(new QueryChangeNotificationBook(title: "Query Change Notification"))
        def notification = queryChangeListener.poll(ChangeOperation.INSERT)
        def entity = notification?.entity()?.orElse(null)

        then:
        notification
        notification.operation() == ChangeOperation.INSERT
        entity.id == saved.id
        entity.title == "Query Change Notification"
        notification.metadata(OracleChangeEventMetadata).orElseThrow().rowId()
    }
}
