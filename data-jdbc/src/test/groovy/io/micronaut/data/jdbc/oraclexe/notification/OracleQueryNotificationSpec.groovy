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
        def notification = objectChangeListener.poll()

        then:
        notification
        notification.id == saved.id
        notification.title == "Continuous Query Notification"
    }

    void "repository finds an entity by Oracle row id"() {
        given:
        def saved = objectChangeRepository.save(new ObjectChangeNotificationBook(title: "Find by row id"))
        objectChangeListener.poll()
        def rowId = objectChangeRepository.findRowIdById(saved.id).orElseThrow()

        when:
        def found = objectChangeRepository.findByRowId(rowId)

        then:
        found.present
        found.get().id == saved.id
        found.get().title == "Find by row id"
    }

    void "change listener receives every entity affected by a bulk update"() {
        given:
        def firstBook = new ObjectChangeNotificationBook(title: "First book")
        def secondBook = new ObjectChangeNotificationBook(title: "Second book")
        def thirdBook = new ObjectChangeNotificationBook(title: "Third book")

        when:
        objectChangeRepository.saveAll([firstBook, secondBook, thirdBook])
        def insertNotifications = [objectChangeListener.poll(), objectChangeListener.poll(), objectChangeListener.poll()]

        then:
        insertNotifications.every { it }
        (insertNotifications*.id as Set) == ([firstBook.id, secondBook.id, thirdBook.id] as Set)
        (insertNotifications*.title as Set) == (["First book", "Second book", "Third book"] as Set)

        when:
        def updated = objectChangeRepository.updateTitleByIds("Bulk updated", [firstBook.id, secondBook.id])
        def updateNotifications = [objectChangeListener.poll(), objectChangeListener.poll()]

        then:
        updated == 2
        updateNotifications.every { it }
        (updateNotifications*.id as Set) == ([firstBook.id, secondBook.id] as Set)
        (updateNotifications*.title as Set) == (["Bulk updated"] as Set)
    }

    void "query change listener receives an entity after an Oracle row is inserted"() {
        when:
        queryChangeRepository.save(new QueryChangeNotificationBook(title: "Ignored by query notification"))
        def saved = queryChangeRepository.save(new QueryChangeNotificationBook(title: "Query Change Notification"))
        def notification = queryChangeListener.poll()

        then:
        notification
        notification.id == saved.id
        notification.title == "Query Change Notification"
    }
}
