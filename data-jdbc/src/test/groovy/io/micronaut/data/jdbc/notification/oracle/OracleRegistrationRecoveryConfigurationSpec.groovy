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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.data.jdbc.notification.oracle.OracleNotificationConfiguration.RegistrationRecoveryConfiguration
import spock.lang.Specification

import java.time.Duration

class OracleRegistrationRecoveryConfigurationSpec extends Specification {

    void "binds global registration recovery settings"() {
        given:
        def context = ApplicationContext.builder().enableDefaultPropertySources(false).properties(properties).start()

        when:
        def configuration = context.getBean(RegistrationRecoveryConfiguration)

        then:
        configuration.maxRetries == retries
        configuration.retryDelay == delay
        configuration.retryDelayMultiplier == multiplier
        configuration.maxRetryDelay == maximumDelay
        context.getBean(RegistrationRecoveryConfiguration).is(configuration)
        context.getBean(OracleNotificationConfiguration).registrationRecovery.is(configuration)

        cleanup:
        context.close()

        where:
        properties                                                                                                   | retries | delay                   | multiplier | maximumDelay
        [:]                                                                                                          | -1      | Duration.ofSeconds(1)   | 2          | Duration.ofSeconds(60)
        [(OracleNotificationConfiguration.PREFIX + '.' + RegistrationRecoveryConfiguration.PREFIX + '.max-retries'): 0]                                        | 0       | Duration.ofSeconds(1)   | 2          | Duration.ofSeconds(60)
        [(OracleNotificationConfiguration.PREFIX + '.' + RegistrationRecoveryConfiguration.PREFIX + '.max-retries'): 5,
         (OracleNotificationConfiguration.PREFIX + '.' + RegistrationRecoveryConfiguration.PREFIX + '.retry-delay'): '500ms',
         (OracleNotificationConfiguration.PREFIX + '.' + RegistrationRecoveryConfiguration.PREFIX + '.retry-delay-multiplier'): 3,
         (OracleNotificationConfiguration.PREFIX + '.' + RegistrationRecoveryConfiguration.PREFIX + '.max-retry-delay'): '30s']                                 | 5       | Duration.ofMillis(500)  | 3          | Duration.ofSeconds(30)
    }

    void "rejects invalid recovery setting #property=#value"() {
        given:
        // Avoid resolving shutdown listeners that depend on the invalid configuration during cleanup.
        def context = ApplicationContext.builder().enableDefaultPropertySources(false)
            .eventsEnabled(false)
            .properties([(OracleNotificationConfiguration.PREFIX + '.' + RegistrationRecoveryConfiguration.PREFIX + '.' + property): value]).build()

        when:
        context.start()
        context.getBean(RegistrationRecoveryConfiguration)

        then:
        def failure = thrown(BeanInstantiationException)
        failure.message.contains(message)

        cleanup:
        context.close()

        where:
        property      | value  | message
        'max-retries' | -2     | 'max-retries must be greater than or equal to -1'
        'retry-delay' | '0s'   | 'retry-delay must be greater than zero'
        'retry-delay' | '-1s'  | 'retry-delay must be greater than zero'
        'retry-delay-multiplier' | 0    | 'retry-delay-multiplier must be greater than or equal to one'
        'retry-delay-multiplier' | -1   | 'retry-delay-multiplier must be greater than or equal to one'
        'max-retry-delay'        | '0s' | 'max-retry-delay must be greater than zero'
        'max-retry-delay'        | '-1s' | 'max-retry-delay must be greater than zero'
    }
}
