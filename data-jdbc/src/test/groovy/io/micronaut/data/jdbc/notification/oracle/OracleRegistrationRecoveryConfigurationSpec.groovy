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
import spock.lang.Specification

import java.time.Duration

class OracleRegistrationRecoveryConfigurationSpec extends Specification {

    void "binds global registration recovery settings"() {
        given:
        def context = ApplicationContext.builder().enableDefaultPropertySources(false).properties(properties).start()

        when:
        def configuration = context.getBean(OracleRegistrationRecoveryConfiguration)

        then:
        configuration.maxRetries == retries
        configuration.retryDelay == delay
        context.getBean(OracleRegistrationRecoveryConfiguration).is(configuration)

        cleanup:
        context.close()

        where:
        properties                                                                                                   | retries | delay
        [:]                                                                                                          | 3       | Duration.ofSeconds(10)
        [(OracleRegistrationRecoveryConfiguration.PREFIX + '.max-retries'): 0]                                        | 0       | Duration.ofSeconds(10)
        [(OracleRegistrationRecoveryConfiguration.PREFIX + '.max-retries'): 5,
         (OracleRegistrationRecoveryConfiguration.PREFIX + '.retry-delay'): '500ms']                                  | 5       | Duration.ofMillis(500)
    }

    void "rejects invalid recovery setting #property=#value"() {
        given:
        def context = ApplicationContext.builder().enableDefaultPropertySources(false)
            .properties([(OracleRegistrationRecoveryConfiguration.PREFIX + '.' + property): value]).build()

        when:
        context.start()
        context.getBean(OracleRegistrationRecoveryConfiguration)

        then:
        def failure = thrown(BeanInstantiationException)
        failure.message.contains(message)

        cleanup:
        context.close()

        where:
        property      | value  | message
        'max-retries' | -1     | 'max-retries must be greater than or equal to zero'
        'retry-delay' | '0s'   | 'retry-delay must be greater than zero'
        'retry-delay' | '-1s'  | 'retry-delay must be greater than zero'
    }
}
