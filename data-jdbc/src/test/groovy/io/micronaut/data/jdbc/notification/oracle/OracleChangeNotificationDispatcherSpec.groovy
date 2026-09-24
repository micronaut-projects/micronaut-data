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
package io.micronaut.data.jdbc.notification.oracle

import io.micronaut.context.BeanContext
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.jdbc.notification.DeferredChangeEvent
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import oracle.jdbc.dcn.DatabaseChangeEvent
import oracle.jdbc.dcn.DatabaseChangeRegistration
import oracle.jdbc.dcn.QueryChangeDescription
import oracle.jdbc.dcn.RowChangeDescription
import oracle.jdbc.dcn.TableChangeDescription
import oracle.jdbc.OracleConnection
import oracle.sql.ROWID
import spock.lang.Specification

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.function.BiConsumer
import java.util.function.Consumer

class OracleChangeNotificationDispatcherSpec extends Specification {

    void "removes a registration when Oracle reports registration deregistration"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        registration.getRegId() >> 41L
        def registrationPurgedHandler = Mock(Consumer)
        def deregistrationHandler = Mock(BiConsumer)
        def queryDeregistrationHandler = Mock(Consumer)
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.DEREG
        event.getAdditionalEventType() >> DatabaseChangeEvent.AdditionalEventType.TIMEOUT
        def dispatcher = dispatcher(definition(), Mock(BeanContext), registration,
            registrationPurgedHandler, deregistrationHandler, queryDeregistrationHandler)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * deregistrationHandler.accept(registration, DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        0 * registrationPurgedHandler.accept(_)
        0 * queryDeregistrationHandler.accept(_)
        0 * event.getTableChangeDescription()
    }

    void "unregisters the enclosing registration when Oracle deregisters its listener query"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        registration.getRegId() >> 42L
        def registrationPurgedHandler = Mock(Consumer)
        def deregistrationHandler = Mock(BiConsumer)
        def queryDeregistrationHandler = Mock(Consumer)
        def query = Mock(QueryChangeDescription)
        query.getQueryId() >> 7L
        query.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.DEREG
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.QUERYCHANGE
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> ([query] as QueryChangeDescription[])
        def dispatcher = dispatcher(definition(), Mock(BeanContext), registration,
            registrationPurgedHandler, deregistrationHandler, queryDeregistrationHandler)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * queryDeregistrationHandler.accept(registration)
        0 * registrationPurgedHandler.accept(_)
        0 * deregistrationHandler.accept(_, _)
        0 * query.getTableChangeDescription()
    }

    void "forwards each registration deregistration reason without dispatching changes"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def deregistrationHandler = Mock(BiConsumer)
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.DEREG
        event.getAdditionalEventType() >> reason
        def dispatcher = dispatcher(definition(), Mock(BeanContext), registration,
            Mock(Consumer), deregistrationHandler, Mock(Consumer))

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * deregistrationHandler.accept(registration, reason)
        0 * event.getTableChangeDescription()

        where:
        reason << [DatabaseChangeEvent.AdditionalEventType.NONE, DatabaseChangeEvent.AdditionalEventType.GROUPING]
    }

    void "registration purge is reported before the notification is dispatched"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def properties = new Properties()
        properties.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, "true")
        def registration = Mock(DatabaseChangeRegistration)
        def sequence = []
        def purgeHandler = { DatabaseChangeRegistration ignored -> sequence << "purged" } as Consumer
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> ([] as QueryChangeDescription[])
        def listenerDefinition = definition(beanDefinition, method, properties)
        def dispatcher = dispatcher(listenerDefinition, beanContext, registration, purgeHandler,
            Mock(BiConsumer), Mock(Consumer), { Runnable command -> command.run() } as Executor,
            new OracleChangeNotificationTaskTracker())

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments ->
            sequence << "listener"
            isInvalidation(arguments)
        })
        sequence == ["purged", "listener"]
    }

    void "query deregistration takes precedence over other query descriptions"() {
        given:
        def registration = Mock(DatabaseChangeRegistration)
        def queryDeregistrationHandler = Mock(Consumer)
        def changedQuery = Mock(QueryChangeDescription)
        changedQuery.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.QUERYCHANGE
        def deregisteredQuery = Mock(QueryChangeDescription)
        deregisteredQuery.getQueryId() >> 17L
        deregisteredQuery.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.DEREG
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.QUERYCHANGE
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> ([changedQuery, deregisteredQuery] as QueryChangeDescription[])
        def dispatcher = dispatcher(definition(), Mock(BeanContext), registration, Mock(Consumer),
            Mock(BiConsumer), queryDeregistrationHandler)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * queryDeregistrationHandler.accept(registration)
        0 * changedQuery.getTableChangeDescription()
    }

    void "dispatches one invalidation when a query notification has no query descriptions"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> queries
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments -> isInvalidation(arguments) })

        where:
        queries << [null, [] as QueryChangeDescription[]]
    }

    void "ignores an unmatched table for object change notification"() {
        given:
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "OTHER_TABLE"
        table.getTableOperations() >> EnumSet.of(TableChangeDescription.TableOperation.UPDATE)
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def dispatcher = dispatcher()

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        0 * table.getRowChangeDescription()
    }

    void "dispatches row operations with their ROWID metadata"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def rowId = Mock(ROWID)
        rowId.stringValue() >> "AAEH7kAAEAAABv3AAA"
        def row = Mock(RowChangeDescription)
        row.getRowid() >> rowId
        row.getRowOperations() >> EnumSet.of(RowChangeDescription.RowOperation.INSERT,
            RowChangeDescription.RowOperation.UPDATE, RowChangeDescription.RowOperation.DELETE)
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK"
        table.getTableOperations() >> EnumSet.of(TableChangeDescription.TableOperation.UPDATE)
        table.getRowChangeDescription() >> ([row] as RowChangeDescription[])
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def operations = []
        def metadataRowIds = []
        method.invoke(bean, _) >> { Object[] arguments ->
            ChangeEvent<?> changeEvent = eventArgument(arguments)
            operations << changeEvent.operation()
            metadataRowIds << changeEvent.metadata(OracleChangeEventMetadata).get().rowId()
            null
        }
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        operations == [ChangeOperation.INSERT, ChangeOperation.UPDATE, ChangeOperation.DELETE]
        metadataRowIds == ["AAEH7kAAEAAABv3AAA"] * 3
    }

    void "uses an empty entity for deletes and defers loading for inserts and updates"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def rowId = Mock(ROWID)
        rowId.stringValue() >> "AAEH7kAAEAAABv3AAA"
        def row = Mock(RowChangeDescription)
        row.getRowid() >> rowId
        row.getRowOperations() >> EnumSet.of(rowOperation)
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK"
        table.getTableOperations() >> EnumSet.of(TableChangeDescription.TableOperation.UPDATE)
        table.getRowChangeDescription() >> ([row] as RowChangeDescription[])
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def events = []
        method.invoke(bean, _) >> { Object[] arguments ->
            ChangeEvent<?> changeEvent = eventArgument(arguments)
            events << changeEvent
            assert changeEvent.metadata(OracleChangeEventMetadata).get().rowId() == "AAEH7kAAEAAABv3AAA"
            assert changeOperation == ChangeOperation.DELETE
                ? changeEvent instanceof DefaultChangeEvent && changeEvent.entity().isEmpty()
                : changeEvent instanceof DeferredChangeEvent
            null
        }
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        events.size() == 1

        where:
        rowOperation << [RowChangeDescription.RowOperation.INSERT, RowChangeDescription.RowOperation.UPDATE,
                         RowChangeDescription.RowOperation.DELETE]
        changeOperation << [ChangeOperation.INSERT, ChangeOperation.UPDATE, ChangeOperation.DELETE]
    }

    void "continues dispatching rows after a listener invocation failure"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def firstRowId = Mock(ROWID)
        firstRowId.stringValue() >> "AAEH7kAAEAAABv3AAA"
        def secondRowId = Mock(ROWID)
        secondRowId.stringValue() >> "AAEH7kAAEAAABv3AAB"
        def firstRow = Mock(RowChangeDescription)
        firstRow.getRowid() >> firstRowId
        firstRow.getRowOperations() >> EnumSet.of(RowChangeDescription.RowOperation.DELETE)
        def secondRow = Mock(RowChangeDescription)
        secondRow.getRowid() >> secondRowId
        secondRow.getRowOperations() >> EnumSet.of(RowChangeDescription.RowOperation.DELETE)
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK"
        table.getTableOperations() >> EnumSet.of(TableChangeDescription.TableOperation.DELETE)
        table.getRowChangeDescription() >> ([firstRow, secondRow] as RowChangeDescription[])
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def invokedRowIds = []
        method.invoke(bean, _) >> { Object[] arguments ->
            String rowId = eventArgument(arguments).metadata(OracleChangeEventMetadata).get().rowId()
            invokedRowIds << rowId
            if (invokedRowIds.size() == 1) {
                throw new IllegalStateException("listener failure")
            }
            null
        }
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        noExceptionThrown()
        invokedRowIds == ["AAEH7kAAEAAABv3AAA", "AAEH7kAAEAAABv3AAB"]
    }

    void "handles executor rejection without leaking an accepted task"() {
        given:
        def taskTracker = new OracleChangeNotificationTaskTracker()
        Executor executor = { Runnable ignored -> throw new RejectedExecutionException("executor closed") } as Executor
        def dispatcher = dispatcher(definition(), Mock(BeanContext), Mock(DatabaseChangeRegistration), Mock(Consumer),
            Mock(BiConsumer), Mock(Consumer), executor, taskTracker)

        when:
        dispatcher.onDatabaseChangeNotification(Mock(DatabaseChangeEvent))

        then:
        noExceptionThrown()
        taskTracker.reportActiveTasks().isEmpty()
    }

    void "ignores callbacks after graceful shutdown starts"() {
        given:
        def taskTracker = new OracleChangeNotificationTaskTracker()
        taskTracker.shutdownGracefully().toCompletableFuture().join()
        def event = Mock(DatabaseChangeEvent)
        def dispatcher = dispatcher(definition(), Mock(BeanContext), Mock(DatabaseChangeRegistration), Mock(Consumer),
            Mock(BiConsumer), Mock(Consumer), { Runnable command -> command.run() } as Executor, taskTracker)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        0 * event.getEventType()
    }

    void "dispatches a full-table notification as one invalidation without row details"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def definition = definition(beanDefinition, method, new Properties())
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK"
        table.getTableOperations() >> EnumSet.of(TableChangeDescription.TableOperation.ALL_ROWS)
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def dispatcher = dispatcher(definition, beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments ->
            ChangeEvent<?> changeEvent = arguments[0] as ChangeEvent<?>
            changeEvent.operation() == ChangeOperation.INVALIDATE &&
                changeEvent.entity().isEmpty() &&
                changeEvent.metadata(OracleChangeEventMetadata).isEmpty()
        })
        0 * table.getRowChangeDescription()
    }

    void "dispatches a #operation notification as one invalidation without row details"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK"
        table.getTableOperations() >> EnumSet.of(operation)
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments -> isInvalidation(arguments) })
        0 * table.getRowChangeDescription()

        where:
        operation << [TableChangeDescription.TableOperation.ALTER, TableChangeDescription.TableOperation.DROP]
    }

    void "dispatches one invalidation when a matching table has no row descriptions"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK"
        table.getTableOperations() >> EnumSet.of(TableChangeDescription.TableOperation.UPDATE)
        table.getRowChangeDescription() >> rows
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments -> isInvalidation(arguments) })

        where:
        rows << [null, [] as RowChangeDescription[]]
    }

    void "suppresses valid row changes when another row has no ROWID"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def rowId = Mock(ROWID)
        def validRow = Mock(RowChangeDescription)
        validRow.getRowid() >> rowId
        validRow.getRowOperations() >> EnumSet.of(RowChangeDescription.RowOperation.INSERT)
        def rowWithoutId = Mock(RowChangeDescription)
        rowWithoutId.getRowid() >> null
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK"
        table.getTableOperations() >> EnumSet.of(TableChangeDescription.TableOperation.INSERT)
        table.getRowChangeDescription() >> ([validRow, rowWithoutId] as RowChangeDescription[])
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments -> isInvalidation(arguments) })
        0 * rowId.stringValue()
    }

    void "dispatches one invalidation when a row has no operation"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def row = Mock(RowChangeDescription)
        row.getRowid() >> Mock(ROWID)
        row.getRowOperations() >> EnumSet.noneOf(RowChangeDescription.RowOperation)
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK"
        table.getTableOperations() >> EnumSet.of(TableChangeDescription.TableOperation.UPDATE)
        table.getRowChangeDescription() >> ([row] as RowChangeDescription[])
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments -> isInvalidation(arguments) })
    }

    void "dispatches one invalidation for a dependent query table"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def properties = new Properties()
        properties.setProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, "true")
        def definition = definition(beanDefinition, method, properties)
        def table = Mock(TableChangeDescription)
        table.getTableName() >> "BOOK_CATEGORY"
        def query = Mock(QueryChangeDescription)
        query.getTableChangeDescription() >> ([table] as TableChangeDescription[])
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> ([query] as QueryChangeDescription[])
        def dispatcher = dispatcher(definition, beanContext)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments ->
            ChangeEvent<?> changeEvent = arguments[0] as ChangeEvent<?>
            changeEvent.operation() == ChangeOperation.INVALIDATE &&
                changeEvent.entity().isEmpty() &&
                changeEvent.metadata(OracleChangeEventMetadata).isEmpty()
        })
        0 * table.getRowChangeDescription()
    }

    void "handles unexpected asynchronous dispatch exceptions"() {
        given:
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> { throw new IllegalStateException("Unexpected dispatch failure") }
        def dispatcher = dispatcher()

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        noExceptionThrown()
    }

    void "allows JVM errors from asynchronous dispatch to propagate"() {
        given:
        def event = Mock(DatabaseChangeEvent)
        event.getTableChangeDescription() >> { throw new AssertionError("Fatal dispatch failure") }
        def dispatcher = dispatcher()

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        def error = thrown(AssertionError)
        error.message == "Fatal dispatch failure"
    }

    private OracleChangeNotificationDispatcher dispatcher() {
        return dispatcher(definition(), Mock(BeanContext))
    }

    private OracleChangeListenerDefinition definition() {
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        return definition(null, method, new Properties())
    }

    private static OracleChangeListenerDefinition definition(BeanDefinition beanDefinition,
                                                               ExecutableMethod method,
                                                               Properties properties) {
        return new OracleChangeListenerDefinition(beanDefinition, method, OracleTableIdentifier.parse("BOOK"),
            "SELECT * FROM BOOK", null, properties,
            new OracleChangeNotificationRenewalPolicy(3600, io.micronaut.data.jdbc.annotation.OracleChangeNotification.RenewalMode.OVERLAPPING, 60, true))
    }

    private OracleChangeNotificationDispatcher dispatcher(OracleChangeListenerDefinition definition,
                                                            BeanContext beanContext) {
        Executor executor = { Runnable command -> command.run() } as Executor
        Consumer<DatabaseChangeRegistration> registrationPurgedHandler = { DatabaseChangeRegistration ignored -> } as Consumer
        BiConsumer<DatabaseChangeRegistration, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler =
            { DatabaseChangeRegistration ignored, DatabaseChangeEvent.AdditionalEventType ignoredType -> } as BiConsumer
        Consumer<DatabaseChangeRegistration> queryDeregistrationHandler = { DatabaseChangeRegistration ignored -> } as Consumer
        return dispatcher(definition, beanContext, Mock(DatabaseChangeRegistration),
            registrationPurgedHandler, deregistrationHandler, queryDeregistrationHandler)
    }

    private OracleChangeNotificationDispatcher dispatcher(OracleChangeListenerDefinition definition,
                                                            BeanContext beanContext,
                                                            DatabaseChangeRegistration registration,
                                                            Consumer<DatabaseChangeRegistration> registrationPurgedHandler,
                                                            BiConsumer<DatabaseChangeRegistration, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler,
                                                            Consumer<DatabaseChangeRegistration> queryDeregistrationHandler) {
        Executor executor = { Runnable command -> command.run() } as Executor
        return dispatcher(definition, beanContext, registration, registrationPurgedHandler,
            deregistrationHandler, queryDeregistrationHandler, executor, new OracleChangeNotificationTaskTracker())
    }

    private OracleChangeNotificationDispatcher dispatcher(OracleChangeListenerDefinition definition,
                                                            BeanContext beanContext,
                                                            DatabaseChangeRegistration registration,
                                                            Consumer<DatabaseChangeRegistration> registrationPurgedHandler,
                                                            BiConsumer<DatabaseChangeRegistration, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler,
                                                            Consumer<DatabaseChangeRegistration> queryDeregistrationHandler,
                                                            Executor executor,
                                                            OracleChangeNotificationTaskTracker taskTracker) {
        return new OracleChangeNotificationDispatcher(
            "inventory",
            definition,
            registration,
            beanContext,
            executor,
            taskTracker,
            registrationPurgedHandler,
            deregistrationHandler,
            queryDeregistrationHandler
        )
    }

    private static boolean isInvalidation(Object[] arguments) {
        ChangeEvent<?> changeEvent = arguments[0] as ChangeEvent<?>
        return changeEvent.operation() == ChangeOperation.INVALIDATE &&
            changeEvent.entity().isEmpty() &&
            changeEvent.metadata(OracleChangeEventMetadata).isEmpty()
    }

    private static ChangeEvent<?> eventArgument(Object value) {
        if (value instanceof ChangeEvent) {
            return value as ChangeEvent<?>
        }
        if (value instanceof Object[]) {
            for (int i = ((Object[]) value).length - 1; i >= 0; i--) {
                ChangeEvent<?> event = eventArgument(((Object[]) value)[i])
                if (event != null) {
                    return event
                }
            }
        } else if (value instanceof Collection) {
            List values = new ArrayList((Collection) value)
            for (int i = values.size() - 1; i >= 0; i--) {
                ChangeEvent<?> event = eventArgument(values[i])
                if (event != null) {
                    return event
                }
            }
        }
        return null
    }
}
