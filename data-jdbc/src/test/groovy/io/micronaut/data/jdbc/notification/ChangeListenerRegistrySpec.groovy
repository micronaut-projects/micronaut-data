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

import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

class ChangeListenerRegistrySpec extends Specification {
    void "collects default and named datasource listeners without creating listener beans"() {
        given:
        def registry = new ChangeListenerRegistry()
        def bean = Mock(BeanDefinition)
        def defaultMethod = listenerMethod(null)
        def namedMethod = listenerMethod('inventory')

        when:
        registry.process(bean, defaultMethod)
        registry.process(bean, namedMethod)

        then:
        registry.getListeners('default')*.method() == [defaultMethod]
        registry.getListeners('inventory')*.method() == [namedMethod]
        registry.getListeners('absent').empty
        registry.getListeners().keySet() == ['default', 'inventory'] as Set
        registry.getListeners('default')[0].entityArgument().type == String
        0 * bean._
    }

    void "registry snapshots are immutable and independent of later discovery"() {
        given:
        def registry = new ChangeListenerRegistry()
        def bean = Stub(BeanDefinition)
        registry.process(bean, listenerMethod(null))
        def byDatasource = registry.getListeners()
        def methods = registry.getListeners('default')

        when:
        registry.process(bean, listenerMethod(null))

        then:
        methods.size() == 1
        byDatasource['default'].size() == 1
        registry.getListeners('default').size() == 2

        when:
        byDatasource.clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        methods.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    void "invalid generated listener arguments fail discovery"() {
        given:
        def registry = new ChangeListenerRegistry()
        def method = Stub(ExecutableMethod) {
            getArguments() >> arguments
            getDescription(true) >> 'void changed(ChangeEvent<Book>)'
        }

        when:
        registry.process(Stub(BeanDefinition), method)

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains(message)
        registry.getListeners().isEmpty()

        where:
        arguments                                | message
        [] as Argument[]                         | 'exactly one ChangeEvent argument'
        [Argument.of(ChangeEvent)] as Argument[] | 'ChangeEvent<E> with a concrete entity type'
    }

    private ExecutableMethod listenerMethod(String datasource) {
        Stub(ExecutableMethod) {
            stringValue(ChangeListener, 'dataSource') >> Optional.ofNullable(datasource)
            getArguments() >> ([Argument.of(ChangeEvent, Argument.of(String))] as Argument[])
        }
    }
}
