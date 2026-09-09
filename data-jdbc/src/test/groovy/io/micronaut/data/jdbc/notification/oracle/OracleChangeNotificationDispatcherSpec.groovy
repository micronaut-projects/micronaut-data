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
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import oracle.jdbc.dcn.DatabaseChangeEvent
import oracle.jdbc.dcn.DatabaseChangeRegistration
import oracle.jdbc.dcn.TableChangeDescription
import spock.lang.Specification

import java.util.concurrent.Executor
import java.util.function.Consumer

class OracleChangeNotificationDispatcherSpec extends Specification {

    void "dispatches a full-table notification as one invalidation without row details"() {
        given:
        def beanDefinition = Mock(BeanDefinition)
        def bean = new Object()
        def beanContext = Mock(BeanContext)
        beanContext.getBean(beanDefinition) >> bean
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def definition = new OracleChangeListenerDefinition(beanDefinition, method, "BOOK", "SELECT * FROM BOOK", null, new Properties())
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
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> "void onChange(ChangeEvent<Book>)"
        def definition = new OracleChangeListenerDefinition(null, method, "BOOK", "SELECT * FROM BOOK", null, new Properties())
        return dispatcher(definition, Mock(BeanContext))
    }

    private OracleChangeNotificationDispatcher dispatcher(OracleChangeListenerDefinition definition,
                                                            BeanContext beanContext) {
        Executor executor = { Runnable command -> command.run() } as Executor
        Consumer<DatabaseChangeRegistration> registrationRemover = { DatabaseChangeRegistration ignored -> } as Consumer
        return new OracleChangeNotificationDispatcher(
            definition,
            Mock(DatabaseChangeRegistration),
            beanContext,
            executor,
            new OracleChangeNotificationShutdownTracker(),
            registrationRemover
        )
    }
}
