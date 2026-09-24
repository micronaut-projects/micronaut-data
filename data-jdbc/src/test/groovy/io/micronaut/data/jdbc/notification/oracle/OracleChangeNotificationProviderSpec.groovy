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
import io.micronaut.context.BeanContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import spock.lang.Specification

import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.Executor

class OracleChangeNotificationProviderSpec extends Specification {

    void "supports Oracle JDBC connections"() {
        given:
        def provider = provider()
        def connection = Mock(Connection)
        connection.isWrapperFor(oracle.jdbc.OracleConnection) >> true

        expect:
        provider.supports(connection)
    }

    void "does not support non-Oracle JDBC connections"() {
        given:
        def provider = provider()
        def connection = Mock(Connection)
        connection.isWrapperFor(oracle.jdbc.OracleConnection) >> false

        expect:
        !provider.supports(connection)
    }

    void "propagates connection capability check failures"() {
        given:
        def provider = provider()
        def connection = Mock(Connection)
        def failure = new SQLException("connection closed")
        connection.isWrapperFor(oracle.jdbc.OracleConnection) >> { throw failure }

        when:
        provider.supports(connection)

        then:
        def thrown = thrown(SQLException)
        thrown.is(failure)
    }

    void "accepts the Micronaut task scheduler"() {
        when:
        provider()

        then:
        noExceptionThrown()
    }

    void "is wired with the named Micronaut task scheduler"() {
        given:
        def context = ApplicationContext.run()

        expect:
        context.getBean(TaskScheduler, Qualifiers.byName(TaskExecutors.SCHEDULED))
        context.getBean(OracleChangeNotificationProvider)

        cleanup:
        context?.close()
    }

    private OracleChangeNotificationProvider provider() {
        new OracleChangeNotificationProvider(Mock(BeanContext), Mock(Executor), Mock(TaskScheduler))
    }
}
