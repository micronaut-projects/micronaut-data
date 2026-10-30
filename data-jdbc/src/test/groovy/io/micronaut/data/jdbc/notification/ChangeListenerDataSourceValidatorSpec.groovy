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
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import jakarta.inject.Singleton
import spock.lang.Specification

import javax.sql.DataSource

class ChangeListenerDataSourceValidatorSpec extends Specification {

    void "disabling notifications skips startup validation and registration (#scenario, enabled=#enabled)"() {
        given:
        def properties = startupProperties(scenario)
        properties['micronaut.data.jdbc.notifications.enabled'] = enabled
        def context = ApplicationContext.builder().enableDefaultPropertySources(false).properties(properties).start()

        expect:
        context.containsBean(TestListener)
        context.getBean(ChangeListenerRegistry).getListeners('inventory').size() == 1
        !context.containsBean(ChangeListenerDataSourceValidator)
        !context.containsBean(ChangeListenerRegistrationHandler)
        context.getActiveBeanRegistrations(ChangeNotificationProviderResolver).empty
        context.getActiveBeanRegistrations(ChangeNotificationProvider).empty

        cleanup:
        context?.close()

        where:
        scenario                 | enabled
        'unsupported datasource' | false
        'unsupported datasource' | 'false'
        'no providers'           | false
        'missing datasource'     | false
    }

    void "enabled notifications retain startup validation (#scenario, enabled=#enabled)"() {
        given:
        def properties = startupProperties(scenario)
        if (enabled != null) {
            properties['micronaut.data.jdbc.notifications.enabled'] = enabled
        }
        def context = ApplicationContext.builder().enableDefaultPropertySources(false).properties(properties).build()

        when:
        context.start()

        then:
        def failure = thrown(RuntimeException)
        failure.message.contains(message)

        cleanup:
        context?.close()

        where:
        scenario                 | enabled | message
        'unsupported datasource' | null    | 'datasource [inventory] has no change notification provider'
        'unsupported datasource' | true    | 'datasource [inventory] has no change notification provider'
        'no providers'           | null    | 'no change notification provider is available'
        'no providers'           | true    | 'no change notification provider is available'
        'missing datasource'     | null    | 'no matching DataSource bean is configured'
        'missing datasource'     | true    | 'no matching DataSource bean is configured'
    }

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
        !datasource || !context.getActiveBeanRegistrations(ChangeListenerRegistrationHandler).empty

        cleanup:
        context?.close()

        where:
        datasource << [false, true]
    }

    void "does not resolve providers when no listeners were discovered"() {
        given:
        def resolver = Mock(BeanProvider)
        def context = Mock(BeanContext)
        def registry = new ChangeListenerRegistry()
        def validator = new ChangeListenerDataSourceValidator(context, resolver, registry)

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
        def registry = new ChangeListenerRegistry()
        def validator = new ChangeListenerDataSourceValidator(context, resolver, registry)
        def method = listenerMethod()
        registry.process(Mock(BeanDefinition), method)

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
        def registry = new ChangeListenerRegistry()
        def validator = new ChangeListenerDataSourceValidator(context, resolver, registry)
        registry.process(Mock(BeanDefinition), listenerMethod())

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
        method.arguments >> ([Argument.of(ChangeEvent, Argument.of(String))] as Argument[])
        method.getDescription(true) >> 'void changed(ChangeEvent<Book>)'
        method
    }

    private static Map<String, Object> startupProperties(String scenario) {
        def properties = [
            'spec.name': 'ChangeListenerDataSourceValidatorSpec',
            'spec.empty-providers': scenario == 'no providers'
        ]
        if (scenario != 'missing datasource') {
            properties.putAll([
                'datasources.inventory.url': 'jdbc:h2:mem:notificationSwitch',
                'datasources.inventory.driver-class-name': 'org.h2.Driver',
                'datasources.inventory.username': 'sa',
                'datasources.inventory.password': ''
            ])
        }
        properties
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ChangeListenerDataSourceValidatorSpec')
    static class TestListener {
        @ChangeListener(dataSource = 'inventory')
        @TestChangeNotification
        void changed(ChangeEvent<Book> event) {
        }
    }

    @MappedEntity
    static class Book {
        @Id
        Long id
    }

    @Factory
    @Requires(property = 'spec.name', value = 'ChangeListenerDataSourceValidatorSpec')
    static class EmptyProviderResolverFactory {
        @Singleton
        @Replaces(ChangeNotificationProviderResolver)
        @Requires(property = 'spec.empty-providers', value = 'true')
        ChangeNotificationProviderResolver resolver() {
            new ChangeNotificationProviderResolver([])
        }
    }
}
