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
package io.micronaut.data.jdbc.notification

import io.micronaut.context.event.StartupEvent
import io.micronaut.core.type.Argument
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.jdbc.runtime.ConnectionCallback
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

import java.sql.Connection

class ChangeListenerMethodProcessorSpec extends Specification {

    void "registers listeners after provider resolution releases its connection"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        def provider = Mock(ChangeNotificationProvider)
        def connection = Mock(Connection)
        def beanDefinition = Mock(BeanDefinition)
        def method = Mock(ExecutableMethod)
        def processor = new ChangeListenerMethodProcessor('default', operations,
            new ChangeNotificationProviderResolver([provider]))
        boolean resolvingProvider = false

        method.stringValue(ChangeListener, 'dataSource') >> Optional.empty()
        method.arguments >> ([Argument.of(ChangeEvent, Argument.of(String))] as Argument[])
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            resolvingProvider = true
            try {
                callback.call(connection)
            } finally {
                resolvingProvider = false
            }
        }
        provider.supports(connection) >> true

        when:
        processor.process(beanDefinition, method)
        processor.onApplicationEvent(Mock(StartupEvent))

        then:
        1 * provider.register('default', operations, { it.size() == 1 && it[0].method().is(method) }) >> {
            assert !resolvingProvider
        }
    }
}
