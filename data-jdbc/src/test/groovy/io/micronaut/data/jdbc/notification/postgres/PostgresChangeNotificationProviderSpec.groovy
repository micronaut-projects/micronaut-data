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

import io.micronaut.context.BeanContext
import io.micronaut.context.BeanProvider
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.json.JsonMapper
import io.micronaut.scheduling.TaskScheduler
import spock.lang.Specification

import java.util.concurrent.Executor

class PostgresChangeNotificationProviderSpec extends Specification {

    void "registration explains the missing JSON mapper dependency"() {
        given:
        def mapperProvider = Mock(BeanProvider<JsonMapper>)
        def connectionFactory = Mock(PostgresNotificationConnectionFactory)
        def operations = Mock(JdbcRepositoryOperations)
        def provider = new PostgresChangeNotificationProvider(Stub(BeanContext), mapperProvider,
            Stub(Executor), Stub(TaskScheduler), new PostgresNotificationConfiguration(), connectionFactory)

        when:
        provider.register('inventory', operations, [])

        then:
        def exception = thrown(ConfigurationException)
        exception.message == 'PostgreSQL notifications for datasource [inventory] require a JsonMapper implementation ' +
            'to decode notification payloads. Add io.micronaut.serde:micronaut-serde-jackson or provide another JsonMapper implementation.'
        1 * mapperProvider.isPresent() >> false
        0 * mapperProvider.get()
        0 * connectionFactory._
        0 * operations._
    }
}
