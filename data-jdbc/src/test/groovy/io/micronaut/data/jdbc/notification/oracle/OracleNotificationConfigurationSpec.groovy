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

class OracleNotificationConfigurationSpec extends Specification {

    void "binds the global notification queue capacity"() {
        given:
        def context = ApplicationContext.builder().enableDefaultPropertySources(false).properties(properties).start()

        expect:
        context.getBean(OracleNotificationConfiguration).queueCapacity == capacity

        cleanup:
        context.close()

        where:
        properties                                                            | capacity
        [:]                                                                   | 1000
        [(OracleNotificationConfiguration.PREFIX + '.queue-capacity'): 1]       | 1
        [(OracleNotificationConfiguration.PREFIX + '.queue-capacity'): 2048]    | 2048
    }

    void "rejects a non-positive notification queue capacity (#capacity)"() {
        given:
        def context = ApplicationContext.builder().enableDefaultPropertySources(false)
            .eventsEnabled(false)
            .properties([(OracleNotificationConfiguration.PREFIX + '.queue-capacity'): capacity]).build()

        when:
        context.start()
        context.getBean(OracleNotificationConfiguration)

        then:
        def failure = thrown(BeanInstantiationException)
        failure.message.contains('queue-capacity must be greater than or equal to one')

        cleanup:
        context.close()

        where:
        capacity << [0, -1]
    }
}
