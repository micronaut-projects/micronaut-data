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
import oracle.jdbc.dcn.QueryChangeDescription
import oracle.jdbc.dcn.RowChangeDescription
import oracle.jdbc.dcn.TableChangeDescription
import oracle.jdbc.OracleConnection
import oracle.sql.ROWID
import spock.lang.Specification

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.function.BiConsumer
import java.util.function.LongConsumer

class OracleChangeNotificationDispatcherSpec extends Specification {

    void "retirement discards queued callbacks only when requested (#discardQueued)"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> new Object()
        def method = Mock(ExecutableMethod)
        def invocations = []
        method.invoke(_, _) >> { Object[] arguments -> invocations << eventArgument(arguments); null }
        List<Runnable> queued = []
        def dispatcher = dispatcher(definition(beanDefinition, method, new Properties()), beanContext,
            Mock(LongConsumer), Mock(BiConsumer), Mock(LongConsumer), Mock(LongConsumer),
            { Runnable task -> queued << task } as Executor, new OracleChangeNotificationTaskTracker())
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE

        when:
        dispatcher.onDatabaseChangeNotification(event)
        dispatcher.retire(discardQueued)
        dispatcher.onDatabaseChangeNotification(event)
        queued.first().run()

        then:
        queued.size() == 1
        invocations.size() == (discardQueued ? 0 : 1)

        where:
        discardQueued << [false, true]
    }

    void "retirement keeps lifecycle callbacks enabled (#eventType)"() {
        given:
        def deregistrations = []
        def shutdowns = []
        def queries = []
        def dispatcher = dispatcher(definition(), Mock(BeanContext), Mock(LongConsumer),
            { Long id, DatabaseChangeEvent.AdditionalEventType ignored -> deregistrations << id } as BiConsumer,
            { long id -> queries << id } as LongConsumer,
            { long id -> shutdowns << id } as LongConsumer,
            { Runnable task -> task.run() } as Executor, new OracleChangeNotificationTaskTracker())
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> eventType
        event.regId >> 41L
        def query = Mock(QueryChangeDescription)
        query.queryChangeEventType >> QueryChangeDescription.QueryChangeEventType.DEREG
        event.queryChangeDescription >> ([query] as QueryChangeDescription[])

        when:
        dispatcher.retire(true)
        dispatcher.onDatabaseChangeNotification(event)

        then:
        deregistrations == (eventType == DatabaseChangeEvent.EventType.DEREG ? [41L] : [])
        shutdowns == (eventType == DatabaseChangeEvent.EventType.SHUTDOWN ? [41L] : [])
        queries == (eventType == DatabaseChangeEvent.EventType.QUERYCHANGE ? [41L] : [])

        where:
        eventType << [DatabaseChangeEvent.EventType.DEREG, DatabaseChangeEvent.EventType.SHUTDOWN,
                      DatabaseChangeEvent.EventType.QUERYCHANGE]
    }

    void "retiring during a running callback does not cancel that callback"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> new Object()
        def method = Mock(ExecutableMethod)
        def completed = false
        OracleChangeNotificationDispatcher dispatcher
        method.invoke(_, _) >> {
            dispatcher.retire(true)
            completed = true
            null
        }
        dispatcher = this.dispatcher(definition(beanDefinition, method, new Properties()), beanContext)
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.OBJCHANGE

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        completed
    }

    void "database shutdown uses effective JDBC options rather than annotation options"() {
        given:
        def properties = new Properties()
        properties.setProperty(OracleConnection.DCN_CLIENT_INIT_CONNECTION, 'true')
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void onChange(ChangeEvent<Book>)'
        def recoveryRequests = []
        def dispatcher = dispatcher(definition(null, method, properties), Mock(BeanContext),
            { long ignored -> } as LongConsumer,
            { Long ignored, DatabaseChangeEvent.AdditionalEventType ignoredType -> } as BiConsumer,
            { long ignored -> } as LongConsumer,
            { long registrationId -> recoveryRequests << registrationId } as LongConsumer,
            { Runnable command -> command.run() } as Executor,
            new OracleChangeNotificationTaskTracker())
        def effective = new Properties()
        effective.putAll(properties)
        effective.setProperty(OracleConnection.NTF_QOS_RELIABLE, 'true')
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.SHUTDOWN
        event.regId >> 41L

        when:
        dispatcher.configureRegistrationOptions(effective)
        dispatcher.onDatabaseChangeNotification(event)

        then:
        recoveryRequests.empty
    }

    void "does not dispatch an instance shutdown as a row change"() {
        given:
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> eventType
        def dispatcher = dispatcher()

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        0 * event.getTableChangeDescription()
        0 * event.getQueryChangeDescription()

        where:
        eventType << [DatabaseChangeEvent.EventType.SHUTDOWN_ANY]
    }

    void "database shutdown delegates retries only for reliable client-initiated notifications"() {
        given:
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def properties = new Properties()
        properties.setProperty(OracleConnection.NTF_QOS_RELIABLE, reliableNotifications.toString())
        properties.setProperty(OracleConnection.DCN_CLIENT_INIT_CONNECTION, clientInitiatedConnection.toString())
        def definition = definition(null, method, properties)
        def recoveryRequests = []
        def dispatcher = dispatcher(definition, Mock(BeanContext),
            { long ignored -> } as LongConsumer,
            { Long ignored, DatabaseChangeEvent.AdditionalEventType ignoredType -> } as BiConsumer,
            { long ignored -> } as LongConsumer,
            { long failed -> recoveryRequests << failed } as LongConsumer,
            { Runnable command -> command.run() } as Executor,
            new OracleChangeNotificationTaskTracker())
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.SHUTDOWN
        event.getRegId() >> 41L

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        recoveryRequests == (driverReconnectRetryEnabled ? [] : [41L])
        0 * method.invoke(_, _)
        0 * event.getTableChangeDescription()

        where:
        reliableNotifications | clientInitiatedConnection | driverReconnectRetryEnabled
        false                 | false                     | false
        true                  | false                     | false
        false                 | true                      | false
        true                  | true                      | true
    }

    void "ignores database startup events"() {
        given:
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.STARTUP
        def dispatcher = dispatcher()

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        0 * event.getTableChangeDescription()
        0 * event.getQueryChangeDescription()
    }

    void "removes a registration when Oracle reports registration deregistration"() {
        given:
        def registrationPurgedHandler = Mock(LongConsumer)
        def deregistrationHandler = Mock(BiConsumer)
        def queryDeregistrationHandler = Mock(LongConsumer)
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.DEREG
        event.getAdditionalEventType() >> DatabaseChangeEvent.AdditionalEventType.TIMEOUT
        event.getRegId() >> 41L
        def dispatcher = dispatcher(definition(), Mock(BeanContext),
            registrationPurgedHandler, deregistrationHandler, queryDeregistrationHandler)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * deregistrationHandler.accept(41L, DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        0 * registrationPurgedHandler.accept(_)
        0 * queryDeregistrationHandler.accept(_)
        0 * event.getTableChangeDescription()
    }

    void "unregisters the enclosing registration when Oracle deregisters its listener query"() {
        given:
        def registrationPurgedHandler = Mock(LongConsumer)
        def deregistrationHandler = Mock(BiConsumer)
        def queryDeregistrationHandler = Mock(LongConsumer)
        def query = Mock(QueryChangeDescription)
        query.getQueryId() >> 7L
        query.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.DEREG
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.QUERYCHANGE
        event.getRegId() >> 42L
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> ([query] as QueryChangeDescription[])
        def dispatcher = dispatcher(definition(), Mock(BeanContext),
            registrationPurgedHandler, deregistrationHandler, queryDeregistrationHandler)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * queryDeregistrationHandler.accept(42L)
        0 * registrationPurgedHandler.accept(_)
        0 * deregistrationHandler.accept(_, _)
        0 * query.getTableChangeDescription()
    }

    void "forwards each registration deregistration reason without dispatching changes"() {
        given:
        def deregistrationHandler = Mock(BiConsumer)
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.DEREG
        event.getAdditionalEventType() >> reason
        event.getRegId() >> 43L
        def dispatcher = dispatcher(definition(), Mock(BeanContext),
            Mock(LongConsumer), deregistrationHandler, Mock(LongConsumer))

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * deregistrationHandler.accept(43L, reason)
        0 * event.getTableChangeDescription()

        where:
        reason << [DatabaseChangeEvent.AdditionalEventType.NONE, DatabaseChangeEvent.AdditionalEventType.GROUPING]
    }

    void "registration purge is reported before an #eventType notification is dispatched"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def properties = new Properties()
        properties.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, "true")
        def sequence = []
        def purgeHandler = { long ignored -> sequence << "purged" } as LongConsumer
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> eventType
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> ([] as QueryChangeDescription[])
        def listenerDefinition = definition(beanDefinition, method, properties)
        def dispatcher = dispatcher(listenerDefinition, beanContext, purgeHandler,
            Mock(BiConsumer), Mock(LongConsumer), { Runnable command -> command.run() } as Executor,
            new OracleChangeNotificationTaskTracker())

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * method.invoke(bean, { Object[] arguments ->
            sequence << "listener"
            isInvalidation(arguments)
        })
        sequence == ["purged", "listener"]

        where:
        eventType << [DatabaseChangeEvent.EventType.OBJCHANGE, DatabaseChangeEvent.EventType.QUERYCHANGE]
    }

    void "one-shot registration timeout is handled as deregistration rather than purge"() {
        given:
        def properties = new Properties()
        properties.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, "true")
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def purgeHandler = Mock(LongConsumer)
        def deregistrationHandler = Mock(BiConsumer)
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.DEREG
        event.getAdditionalEventType() >> DatabaseChangeEvent.AdditionalEventType.TIMEOUT
        event.getRegId() >> 45L
        def dispatcher = dispatcher(definition(null, method, properties), Mock(BeanContext),
            purgeHandler, deregistrationHandler, Mock(LongConsumer))

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * deregistrationHandler.accept(45L, DatabaseChangeEvent.AdditionalEventType.TIMEOUT)
        0 * purgeHandler.accept(_)
        0 * event.getTableChangeDescription()
    }

    void "one-shot registration does not purge on #eventType"() {
        given:
        def properties = new Properties()
        properties.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, "true")
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def purgeHandler = Mock(LongConsumer)
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> eventType
        def dispatcher = dispatcher(definition(null, method, properties), Mock(BeanContext),
            purgeHandler, Mock(BiConsumer), Mock(LongConsumer))

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        0 * purgeHandler.accept(_)
        0 * event.getTableChangeDescription()

        where:
        eventType << [DatabaseChangeEvent.EventType.STARTUP,
                      DatabaseChangeEvent.EventType.SHUTDOWN,
                      DatabaseChangeEvent.EventType.SHUTDOWN_ANY]
    }

    void "one-shot query deregistration does not suppress query cleanup"() {
        given:
        def properties = new Properties()
        properties.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, "true")
        properties.setProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, "true")
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def purgeHandler = Mock(LongConsumer)
        def queryDeregistrationHandler = Mock(LongConsumer)
        def query = Mock(QueryChangeDescription)
        query.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.DEREG
        query.getQueryId() >> 18L
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.QUERYCHANGE
        event.getRegId() >> 46L
        event.getQueryChangeDescription() >> ([query] as QueryChangeDescription[])
        def dispatcher = dispatcher(definition(null, method, properties), Mock(BeanContext),
            purgeHandler, Mock(BiConsumer), queryDeregistrationHandler)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * queryDeregistrationHandler.accept(46L)
        0 * purgeHandler.accept(_)
    }

    void "query deregistration takes precedence over other query descriptions"() {
        given:
        def queryDeregistrationHandler = Mock(LongConsumer)
        def changedQuery = Mock(QueryChangeDescription)
        changedQuery.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.QUERYCHANGE
        def deregisteredQuery = Mock(QueryChangeDescription)
        deregisteredQuery.getQueryId() >> 17L
        deregisteredQuery.getQueryChangeEventType() >> QueryChangeDescription.QueryChangeEventType.DEREG
        def event = Mock(DatabaseChangeEvent)
        event.getEventType() >> DatabaseChangeEvent.EventType.QUERYCHANGE
        event.getTableChangeDescription() >> null
        event.getQueryChangeDescription() >> ([changedQuery, deregisteredQuery] as QueryChangeDescription[])
        event.getRegId() >> 44L
        def dispatcher = dispatcher(definition(), Mock(BeanContext), Mock(LongConsumer),
            Mock(BiConsumer), queryDeregistrationHandler)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        1 * queryDeregistrationHandler.accept(44L)
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
        def dispatcher = dispatcher(definition(), Mock(BeanContext), Mock(LongConsumer),
            Mock(BiConsumer), Mock(LongConsumer), executor, taskTracker)

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
        def dispatcher = dispatcher(definition(), Mock(BeanContext), Mock(LongConsumer),
            Mock(BiConsumer), Mock(LongConsumer), { Runnable command -> command.run() } as Executor, taskTracker)

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        0 * event.getEventType()
    }

    void "does not wait for queued callbacks during graceful shutdown"() {
        given:
        def taskTracker = new OracleChangeNotificationTaskTracker()
        List<Runnable> queued = []
        def shutdownRequests = []
        Executor executor = { Runnable command -> queued << command } as Executor
        def dispatcher = dispatcher(definition(), Mock(BeanContext), Mock(LongConsumer),
            Mock(BiConsumer), Mock(LongConsumer),
            { long registrationId -> shutdownRequests << registrationId } as LongConsumer,
            executor, taskTracker)
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.SHUTDOWN
        event.regId >> 41L

        when:
        dispatcher.onDatabaseChangeNotification(event)
        def completion = taskTracker.shutdownGracefully().toCompletableFuture()

        then:
        queued.size() == 1
        completion.done

        when:
        queued.first().run()

        then:
        shutdownRequests.empty
        taskTracker.reportActiveTasks().getAsLong() == 0L
    }

    void "waits for a callback that started before graceful shutdown"() {
        given:
        def taskTracker = new OracleChangeNotificationTaskTracker()
        def completionDuringDispatch
        boolean runningTaskWasCounted = false
        def dispatcher = dispatcher(definition(), Mock(BeanContext), Mock(LongConsumer),
            Mock(BiConsumer), Mock(LongConsumer),
            { long ignored ->
                completionDuringDispatch = taskTracker.shutdownGracefully().toCompletableFuture()
                runningTaskWasCounted = !completionDuringDispatch.done && taskTracker.reportActiveTasks().getAsLong() == 1L
            } as LongConsumer,
            { Runnable command -> command.run() } as Executor, taskTracker)
        def event = Mock(DatabaseChangeEvent)
        event.eventType >> DatabaseChangeEvent.EventType.SHUTDOWN
        event.regId >> 41L

        when:
        dispatcher.onDatabaseChangeNotification(event)

        then:
        runningTaskWasCounted
        completionDuringDispatch != null
        completionDuringDispatch.done
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
            new OracleChangeNotificationRenewalPolicy(3600, io.micronaut.data.jdbc.annotation.OracleChangeNotification.RenewalMode.OVERLAPPING, 60))
    }

    private OracleChangeNotificationDispatcher dispatcher(OracleChangeListenerDefinition definition,
                                                            BeanContext beanContext) {
        Executor executor = { Runnable command -> command.run() } as Executor
        LongConsumer registrationPurgedHandler = { long ignored -> } as LongConsumer
        BiConsumer<Long, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler =
            { Long ignored, DatabaseChangeEvent.AdditionalEventType ignoredType -> } as BiConsumer
        LongConsumer queryDeregistrationHandler = { long ignored -> } as LongConsumer
        return dispatcher(definition, beanContext,
            registrationPurgedHandler, deregistrationHandler, queryDeregistrationHandler)
    }

    private OracleChangeNotificationDispatcher dispatcher(OracleChangeListenerDefinition definition,
                                                            BeanContext beanContext,
                                                            LongConsumer registrationPurgedHandler,
                                                            BiConsumer<Long, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler,
                                                            LongConsumer queryDeregistrationHandler) {
        Executor executor = { Runnable command -> command.run() } as Executor
        return dispatcher(definition, beanContext, registrationPurgedHandler,
            deregistrationHandler, queryDeregistrationHandler,
            { long ignored -> } as LongConsumer,
            executor, new OracleChangeNotificationTaskTracker())
    }

    private OracleChangeNotificationDispatcher dispatcher(OracleChangeListenerDefinition definition,
                                                            BeanContext beanContext,
                                                            LongConsumer registrationPurgedHandler,
                                                            BiConsumer<Long, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler,
                                                            LongConsumer queryDeregistrationHandler,
                                                            Executor executor,
                                                            OracleChangeNotificationTaskTracker taskTracker) {
        return dispatcher(definition, beanContext, registrationPurgedHandler,
            deregistrationHandler, queryDeregistrationHandler,
            { long ignored -> } as LongConsumer, executor, taskTracker)
    }

    private OracleChangeNotificationDispatcher dispatcher(OracleChangeListenerDefinition definition,
                                                            BeanContext beanContext,
                                                            LongConsumer registrationPurgedHandler,
                                                            BiConsumer<Long, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler,
                                                            LongConsumer queryDeregistrationHandler,
                                                            LongConsumer databaseShutdownHandler,
                                                            Executor executor,
                                                            OracleChangeNotificationTaskTracker taskTracker) {
        def dispatcher = new OracleChangeNotificationDispatcher(
            "inventory",
            definition,
            beanContext,
            executor,
            taskTracker,
            registrationPurgedHandler,
            deregistrationHandler,
            queryDeregistrationHandler,
            databaseShutdownHandler
        )
        dispatcher.configureRegistrationOptions(definition.registrationProperties())
        return dispatcher
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
