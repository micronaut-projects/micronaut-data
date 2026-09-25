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

import io.micronaut.context.BeanContext
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.runtime.ConnectionCallback
import io.micronaut.data.jdbc.runtime.JdbcOperations
import oracle.jdbc.NotificationRegistration
import oracle.jdbc.OracleConnection
import oracle.jdbc.dcn.DatabaseChangeRegistration
import spock.lang.Specification

import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.Executor
import java.util.function.LongSupplier

class OracleChangeNotificationRegistrarSpec extends Specification {

    void "does not acquire a connection for a registration already closed by the driver"() {
        given:
        def operations = Mock(JdbcOperations)
        def registration = Mock(DatabaseChangeRegistration)
        registration.state >> NotificationRegistration.RegistrationState.CLOSED

        when:
        registrar(operations).unregisterRegistration(registration)

        then:
        0 * operations.execute(_ as ConnectionCallback)
    }

    void "unregisters an active registration using a datasource connection"() {
        given:
        def operations = Mock(JdbcOperations)
        def registration = Mock(DatabaseChangeRegistration)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        registration.state >> NotificationRegistration.RegistrationState.ACTIVE
        connection.unwrap(OracleConnection) >> oracleConnection

        when:
        registrar(operations).unregisterRegistration(registration)

        then:
        1 * operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        1 * oracleConnection.unregisterDatabaseChangeNotification(registration)
    }

    void "treats a registration missing from Oracle Database as already unregistered"() {
        given:
        def operations = Mock(JdbcOperations)
        def registration = Mock(DatabaseChangeRegistration)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        registration.state >> NotificationRegistration.RegistrationState.ACTIVE
        connection.unwrap(OracleConnection) >> oracleConnection
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            try {
                callback.call(connection)
            } catch (SQLException e) {
                throw new DataAccessException('Error executing SQL Callback', e)
            }
        }

        when:
        registrar(operations).unregisterRegistration(registration)

        then:
        1 * oracleConnection.unregisterDatabaseChangeNotification(registration) >> {
            throw new SQLException('Specified registration id does not exist', '72000', 29970)
        }
        noExceptionThrown()
    }

    void "propagates other Oracle Database deregistration failures"() {
        given:
        def operations = Mock(JdbcOperations)
        def registration = Mock(DatabaseChangeRegistration)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def failure = new SQLException('Unable to unregister', '72000', 29972)
        registration.state >> NotificationRegistration.RegistrationState.ACTIVE
        connection.unwrap(OracleConnection) >> oracleConnection
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            try {
                callback.call(connection)
            } catch (SQLException e) {
                throw new DataAccessException('Error executing SQL Callback', e)
            }
        }

        when:
        registrar(operations).unregisterRegistration(registration)

        then:
        1 * oracleConnection.unregisterDatabaseChangeNotification(registration) >> { throw failure }
        def thrownFailure = thrown(DataAccessException)
        thrownFailure.cause.is(failure)
    }

    private OracleChangeNotificationRegistrar registrar(JdbcOperations operations) {
        new OracleChangeNotificationRegistrar('default', operations, Mock(BeanContext), Mock(Executor),
            new OracleChangeNotificationTaskTracker(), { 0L } as LongSupplier)
    }
}
