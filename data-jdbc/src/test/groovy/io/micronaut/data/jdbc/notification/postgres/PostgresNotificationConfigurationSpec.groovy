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
package io.micronaut.data.jdbc.notification.postgres

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll
import java.time.Duration

class PostgresNotificationConfigurationSpec extends Specification {
    void "defaults use finite polling and unlimited recovery with exponential backoff"() {
        when:
        def config = new PostgresNotificationConfiguration()

        then:
        !config.schemaGenerate
        config.schemaGenerateMode == PostgresNotificationConfiguration.SchemaGenerationMode.ROW
        config.queueCapacity == 1000
        config.pollTimeout == Duration.ofSeconds(1)
        config.validationInterval == Duration.ofSeconds(30)
        config.validationTimeout == Duration.ofSeconds(5)
        config.connectionRecovery.maxRetries == -1
        config.connectionRecovery.retryDelayMultiplier == 2
        config.connectionRecovery.retryDelay == Duration.ofSeconds(1)
        config.connectionRecovery.maxRetryDelay == Duration.ofSeconds(60)
    }

    void "PostgreSQL beans are available when driver present is #driverPresent"() {
        given:
        boolean driverVisible = driverPresent
        def classLoader = new ClassLoader(getClass().classLoader) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!driverVisible && name.startsWith('org.postgresql.')) {
                    throw new ClassNotFoundException(name)
                }
                if (!name.startsWith('io.micronaut.data.jdbc.notification.postgres.')) {
                    return super.loadClass(name, resolve)
                }
                synchronized (getClassLoadingLock(name)) {
                    def loaded = findLoadedClass(name)
                    if (loaded == null) {
                        def resource = name.replace('.', '/') + '.class'
                        def bytes = parent.getResourceAsStream(resource)?.bytes
                        if (bytes == null) {
                            throw new ClassNotFoundException(name)
                        }
                        loaded = defineClass(name, bytes, 0, bytes.length)
                    }
                    if (resolve) {
                        resolveClass(loaded)
                    }
                    return loaded
                }
            }
        }
        def context = ApplicationContext.builder().classLoader(classLoader)
                .enableDefaultPropertySources(false)
                .properties(['micronaut.data.jdbc.notifications.postgres.schema-generate': true]).start()

        expect:
        context.containsBean(classLoader.loadClass(PostgresNotificationConfiguration.name)) == driverPresent
        context.containsBean(classLoader.loadClass(PostgresNotificationConfiguration.ConnectionRecoveryConfiguration.name)) == driverPresent
        context.containsBean(classLoader.loadClass(PostgresNotificationSchemaGenerator.name)) == driverPresent

        cleanup:
        context?.close()

        where:
        driverPresent << [false, true]
    }

    void "schema generator is available when the driver is present and schema generation is enabled"() {
        given:
        def context = ApplicationContext.run(['micronaut.data.jdbc.notifications.postgres.schema-generate': true])

        expect:
        context.containsBean(PostgresNotificationConfiguration)
        context.containsBean(PostgresNotificationConfiguration.ConnectionRecoveryConfiguration)
        context.containsBean(PostgresNotificationSchemaGenerator)

        cleanup:
        context.close()
    }

    void "connection recovery binds as the same nested bean used by notification configuration"() {
        given:
        def context = ApplicationContext.run([
                'micronaut.data.jdbc.notifications.postgres.connection-recovery.max-retries'           : 3,
                'micronaut.data.jdbc.notifications.postgres.connection-recovery.retry-delay'           : '500ms',
                'micronaut.data.jdbc.notifications.postgres.connection-recovery.retry-delay-multiplier': 4,
                'micronaut.data.jdbc.notifications.postgres.connection-recovery.max-retry-delay'       : '20s'
        ])

        expect:
        def recovery = context.getBean(PostgresNotificationConfiguration.ConnectionRecoveryConfiguration)
        context.getBean(PostgresNotificationConfiguration).connectionRecovery.is(recovery)
        recovery.maxRetries == 3
        recovery.retryDelay == Duration.ofMillis(500)
        recovery.retryDelayMultiplier == 4
        recovery.maxRetryDelay == Duration.ofSeconds(20)

        cleanup:
        context.close()
    }

    void "recovery limit and multiplier accept supported boundary values"() {
        given:
        def config = new PostgresNotificationConfiguration()

        when:
        config.connectionRecovery.maxRetries = retries
        config.connectionRecovery.retryDelayMultiplier = multiplier

        then:
        config.connectionRecovery.maxRetries == retries
        config.connectionRecovery.retryDelayMultiplier == multiplier

        where:
        retries           | multiplier
        -1                | 1
        0                 | 2
        Integer.MAX_VALUE | Integer.MAX_VALUE
    }

    void "queue capacity binds from configuration with value #capacity"() {
        given:
        def context = ApplicationContext.run([
                'micronaut.data.jdbc.notifications.postgres.queue-capacity': capacity
        ])

        expect:
        context.getBean(PostgresNotificationConfiguration).queueCapacity == capacity

        cleanup:
        context.close()

        where:
        capacity << [1, 17, 2048]
    }

    @Unroll
    void "invalid #property value #value is rejected"() {
        when:
        def config = new PostgresNotificationConfiguration()
        def target = property in ['retryDelay', 'maxRetryDelay', 'maxRetries', 'retryDelayMultiplier'] ? config.connectionRecovery : config
        target."$property" = value

        then:
        thrown(IllegalArgumentException)

        where:
        property               | value
        'pollTimeout'          | Duration.ZERO
        'pollTimeout'          | Duration.ofNanos(1)
        'pollTimeout'          | Duration.ofMillis(Integer.MAX_VALUE + 1L)
        'validationInterval'   | Duration.ZERO
        'validationTimeout'    | Duration.ofNanos(1)
        'validationTimeout'    | Duration.ofMillis(Integer.MAX_VALUE + 1L)
        'retryDelay'           | Duration.ofMillis(-1)
        'maxRetryDelay'        | Duration.ZERO
        'queueCapacity'        | 0
        'queueCapacity'        | -1
        'maxRetries'           | -2
        'retryDelayMultiplier' | 0
        'retryDelayMultiplier' | -1
    }
}
