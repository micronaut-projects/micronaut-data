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
import io.micronaut.transaction.SynchronousTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.util.concurrent.TimeUnit

class OracleQueryNotificationSpec extends Specification implements OracleTestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext context

    @Shared
    ObjectChangeNotificationBookRepository objectChangeRepository

    @Shared
    QueryChangeNotificationBookRepository queryChangeRepository

    @Shared
    CatalogProductRepository catalogProductRepository

    @Shared
    CatalogCategoryRepository catalogCategoryRepository

    @Shared
    ObjectChangeNotificationBookListener objectChangeListener

    @Shared
    QueryChangeNotificationBookListener queryChangeListener

    @Shared
    CatalogProductListener catalogProductListener

    @Shared
    SynchronousTransactionManager<Connection> transactionManager

    @Override
    List<String> packages() {
        return Arrays.asList(getClass().package.name)
    }

    def setupSpec() {
        grantChangeNotificationPrivilege()
        context = ApplicationContext.run(properties + ["query-notification.enabled": "true"])
        objectChangeRepository = context.getBean(ObjectChangeNotificationBookRepository)
        queryChangeRepository = context.getBean(QueryChangeNotificationBookRepository)
        catalogProductRepository = context.getBean(CatalogProductRepository)
        catalogCategoryRepository = context.getBean(CatalogCategoryRepository)
        objectChangeListener = context.getBean(ObjectChangeNotificationBookListener)
        queryChangeListener = context.getBean(QueryChangeNotificationBookListener)
        catalogProductListener = context.getBean(CatalogProductListener)
        transactionManager = context.getBean(SynchronousTransactionManager)
    }

    void cleanup() {
        objectChangeRepository.deleteAll()
        queryChangeRepository.deleteAll()
        catalogProductRepository.deleteAll()
        catalogCategoryRepository.deleteAll()
    }

    def cleanupSpec() {
        context?.close()
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

    void "query change listener ignores a row outside the registered query"() {
        given:
        queryChangeListener.discardNotifications(250, TimeUnit.MILLISECONDS)

        when:
        queryChangeRepository.save(new QueryChangeNotificationBook(title: "Ignored by query notification"))

        then:
        queryChangeListener.poll(1, TimeUnit.SECONDS) == null
    }

    void "query change listener receives an update when a row enters the registered query"() {
        given:
        def book = queryChangeRepository.save(new QueryChangeNotificationBook(title: "Ignored by query notification"))
        assert queryChangeListener.poll(1, TimeUnit.SECONDS) == null

        when:
        book.title = "Query Change Notification"
        queryChangeRepository.update(book)
        def notification = queryChangeListener.poll(ChangeOperation.UPDATE)
        def entity = notification?.entity()?.orElse(null)

        then:
        notification
        entity.id == book.id
        entity.title == "Query Change Notification"
        notification.metadata(OracleChangeEventMetadata).orElseThrow().rowId()
    }

    void "query change listener receives an update when a row leaves the registered query"() {
        given:
        def book = queryChangeRepository.save(new QueryChangeNotificationBook(title: "Query Change Notification"))
        assert queryChangeListener.poll(ChangeOperation.INSERT)

        when:
        book.title = "Ignored by query notification"
        queryChangeRepository.update(book)
        def notification = queryChangeListener.poll(ChangeOperation.UPDATE)
        def entity = notification?.entity()?.orElse(null)

        then:
        notification
        entity.id == book.id
        entity.title == "Ignored by query notification"
        notification.metadata(OracleChangeEventMetadata).orElseThrow().rowId()
    }

    void "query change listener receives a delete without entity state"() {
        given:
        def book = queryChangeRepository.save(new QueryChangeNotificationBook(title: "Query Change Notification"))
        assert queryChangeListener.poll(ChangeOperation.INSERT)

        when:
        queryChangeRepository.deleteById(book.id)
        def notification = queryChangeListener.poll(ChangeOperation.DELETE)

        then:
        notification
        notification.entity().isEmpty()
        notification.metadata(OracleChangeEventMetadata).orElseThrow().rowId()
    }

    void "query change listener dispatches invalidation for a dependent table"() {
        given:
        CatalogCategory catalogCategory = catalogCategoryRepository.save(new CatalogCategory(enabled: true))
        catalogProductRepository.save(new CatalogProduct(categoryId: catalogCategory.id))
        assert catalogProductListener.poll(ChangeOperation.INSERT)

        when:
        catalogCategory.enabled = false
        catalogCategoryRepository.update(catalogCategory)

        then:
        def notification = catalogProductListener.poll(ChangeOperation.INVALIDATE)
        notification
        notification.entity().isEmpty()
        notification.metadata(OracleChangeEventMetadata).isEmpty()
    }

    void "dependent table invalidation suppresses row events from the same database event"() {
        given:
        CatalogCategory firstCategory = catalogCategoryRepository.save(new CatalogCategory(enabled: true))
        CatalogCategory secondCategory = catalogCategoryRepository.save(new CatalogCategory(enabled: true))
        CatalogProduct product = catalogProductRepository.save(new CatalogProduct(categoryId: firstCategory.id))
        assert catalogProductListener.poll(ChangeOperation.INSERT)
        catalogProductListener.discardNotifications(250, TimeUnit.MILLISECONDS)

        when:
        transactionManager.executeWrite { status ->
            Connection connection = status.connection
            connection.prepareStatement('UPDATE CATALOG_PRODUCT SET CATEGORY_ID = ? WHERE ID = ?').withCloseable { statement ->
                statement.setLong(1, secondCategory.id)
                statement.setLong(2, product.id)
                assert statement.executeUpdate() == 1
            }
            connection.prepareStatement('UPDATE CATALOG_CATEGORY SET ENABLED = 0 WHERE ID = ?').withCloseable { statement ->
                statement.setLong(1, firstCategory.id)
                assert statement.executeUpdate() == 1
            }
            null
        }
        def notification = catalogProductListener.poll(10, TimeUnit.SECONDS)

        then:
        notification
        notification.operation() == ChangeOperation.INVALIDATE
        notification.entity().isEmpty()
        notification.metadata(OracleChangeEventMetadata).isEmpty()
        catalogProductListener.poll(1, TimeUnit.SECONDS) == null
    }
}
