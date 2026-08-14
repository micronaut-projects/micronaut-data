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
import io.micronaut.inject.ExecutableMethod
import oracle.jdbc.dcn.DatabaseChangeEvent
import oracle.jdbc.dcn.DatabaseChangeRegistration
import spock.lang.Specification

import java.util.concurrent.Executor
import java.util.function.Consumer

class OracleChangeNotificationDispatcherSpec extends Specification {

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
        Executor executor = { Runnable command -> command.run() } as Executor
        Consumer<DatabaseChangeRegistration> registrationRemover = { DatabaseChangeRegistration ignored -> } as Consumer
        return new OracleChangeNotificationDispatcher(
            definition,
            Mock(DatabaseChangeRegistration),
            Mock(BeanContext),
            executor,
            new OracleChangeNotificationShutdownTracker(),
            registrationRemover
        )
    }
}
