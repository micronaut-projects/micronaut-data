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

import spock.lang.Specification
import spock.lang.Unroll
import java.time.Duration

class PostgresNotificationConfigurationSpec extends Specification {
    void "defaults use finite polling and unlimited recovery with exponential backoff"() {
        when:
        def config = new PostgresNotificationConfiguration()

        then:
        !config.schemaGenerate
        config.pollTimeout == Duration.ofSeconds(1)
        config.maxRetries == -1
        config.retryDelayMultiplier == 2
        config.retryDelay == Duration.ofSeconds(1)
        config.maxRetryDelay == Duration.ofSeconds(60)
    }

    void "recovery limit and multiplier accept supported boundary values"() {
        given:
        def config = new PostgresNotificationConfiguration()

        when:
        config.maxRetries = retries
        config.retryDelayMultiplier = multiplier

        then:
        config.maxRetries == retries
        config.retryDelayMultiplier == multiplier

        where:
        retries           | multiplier
        -1                | 1
        0                 | 2
        Integer.MAX_VALUE | Integer.MAX_VALUE
    }

    @Unroll
    void "invalid #property value #value is rejected"() {
        when:
        def config = new PostgresNotificationConfiguration()
        config."$property" = value

        then:
        thrown(IllegalArgumentException)

        where:
        property               | value
        'pollTimeout'          | Duration.ZERO
        'pollTimeout'          | Duration.ofNanos(1)
        'pollTimeout'          | Duration.ofMillis(Integer.MAX_VALUE + 1L)
        'retryDelay'           | Duration.ofMillis(-1)
        'maxRetryDelay'        | Duration.ZERO
        'maxRetries'           | -2
        'retryDelayMultiplier' | 0
        'retryDelayMultiplier' | -1
    }
}
