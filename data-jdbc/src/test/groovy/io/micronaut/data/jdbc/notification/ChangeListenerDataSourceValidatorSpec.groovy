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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanProvider
import io.micronaut.context.event.StartupEvent
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

import javax.sql.DataSource

class ChangeListenerDataSourceValidatorSpec extends Specification {

    void "startup without change listeners leaves providers uninitialized with datasource=#datasource"() {
        given:
        def properties = datasource ? [
            'datasources.default.url': 'jdbc:h2:mem:noChangeListeners',
            'datasources.default.driver-class-name': 'org.h2.Driver',
            'datasources.default.username': 'sa',
            'datasources.default.password': ''
        ] : [:]
        def context = ApplicationContext.run(properties)

        expect:
        context.containsBean(ChangeNotificationProvider)
        !context.getActiveBeanRegistrations(ChangeListenerDataSourceValidator).empty
        context.getActiveBeanRegistrations(ChangeNotificationProviderResolver).empty
        context.getActiveBeanRegistrations(ChangeNotificationProvider).empty
        !datasource || !context.getActiveBeanRegistrations(ChangeListenerMethodProcessor).empty

        cleanup:
        context?.close()

        where:
        datasource << [false, true]
    }

    void "does not resolve providers when no listeners were discovered"() {
        given:
        def resolver = Mock(BeanProvider)
        def context = Mock(BeanContext)
        def validator = new ChangeListenerDataSourceValidator(context, resolver)

        when:
        validator.onApplicationEvent(Mock(StartupEvent))

        then:
        0 * resolver._
        0 * context._
    }

    void "reports a missing datasource before resolving providers"() {
        given:
        def resolver = Mock(BeanProvider)
        def context = Mock(BeanContext)
        def validator = new ChangeListenerDataSourceValidator(context, resolver)
        def method = listenerMethod()
        validator.process(Mock(BeanDefinition), method)

        when:
        validator.onApplicationEvent(Mock(StartupEvent))

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('datasource [inventory] but no matching DataSource bean is configured')
        0 * resolver._
    }

    void "resolves providers for discovered listeners and rejects missing providers"() {
        given:
        def resolver = Mock(BeanProvider)
        def context = Mock(BeanContext)
        context.containsBean(DataSource, _) >> true
        def validator = new ChangeListenerDataSourceValidator(context, resolver)
        validator.process(Mock(BeanDefinition), listenerMethod())

        when:
        validator.onApplicationEvent(Mock(StartupEvent))

        then:
        1 * resolver.get() >> new ChangeNotificationProviderResolver([])
        def failure = thrown(IllegalStateException)
        failure.message.contains('no change notification provider is available')
    }

    private ExecutableMethod listenerMethod() {
        def method = Mock(ExecutableMethod)
        method.stringValue(ChangeListener, 'dataSource') >> Optional.of('inventory')
        method.getDescription(true) >> 'void changed(ChangeEvent<Book>)'
        method
    }
}
