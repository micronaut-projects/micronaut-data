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
import io.micronaut.inject.ExecutableMethod
import io.micronaut.scheduling.TaskScheduler
import oracle.jdbc.NotificationRegistration
import oracle.jdbc.OracleConnection
import oracle.jdbc.dcn.DatabaseChangeRegistration
import oracle.jdbc.dcn.DatabaseChangeListener
import spock.lang.Specification

import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.Executor

class OracleChangeNotificationRegistrarSpec extends Specification {

    void "rejects unsupported or conflicting connection-level registration settings"() {
        given:
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void changed(ChangeEvent<Book>)'
        def requested = new Properties()
        requested.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, 'true')
        requested.setProperty(OracleConnection.NTF_TIMEOUT, '0')
        def connectionProperties = new Properties()
        connectionProperties.setProperty(option, overrideValue)
        def definition = new OracleChangeListenerDefinition(null, method, null, null, null, requested)

        when:
        OracleChangeNotificationOptionsValidator.validateConnectionOptions(connectionProperties, definition, 'default')

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains(option)
        failure.message.contains('datasource [default]')
        failure.message.contains('listener method [void changed(ChangeEvent<Book>)]')

        where:
        option                                         | overrideValue
        OracleConnection.NTF_TIMEOUT                   | '120'
        OracleConnection.NTF_TIMEOUT                   | '60'
        OracleConnection.DCN_NOTIFY_ROWIDS             | 'false'
        OracleConnection.DCN_NOTIFY_CHANGELAG          | '1'
        OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION | 'true'
        OracleConnection.NTF_QOS_PURGE_ON_NTFN         | 'true'
        OracleConnection.DCN_CLIENT_INIT_REGID         | '42'
        OracleConnection.NTF_GROUPING_CLASS            | OracleConnection.NTF_GROUPING_CLASS_TIME
        OracleConnection.NTF_GROUPING_VALUE            | '30'
        OracleConnection.NTF_GROUPING_TYPE             | OracleConnection.NTF_GROUPING_TYPE_LAST
        OracleConnection.NTF_GROUPING_REPEAT_TIME      | '2'
        OracleConnection.NTF_GROUPING_START_TIME       | 'tomorrow'
        OracleConnection.DCN_PULL_NOTIFICATIONS        | 'true'
        OracleConnection.DCN_PULL_QUEUE_NAME           | 'CHANGES'
    }

    void "accepts non-conflicting effective driver options"() {
        given:
        def requested = new Properties()
        requested.setProperty(OracleConnection.NTF_TIMEOUT, '0')
        requested.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, 'true')
        def connectionProperties = new Properties()
        connectionProperties.setProperty(OracleConnection.NTF_QOS_RELIABLE, 'true')
        connectionProperties.setProperty(OracleConnection.NTF_GROUPING_CLASS, OracleConnection.NTF_GROUPING_CLASS_NONE)
        connectionProperties.setProperty(OracleConnection.DCN_PULL_NOTIFICATIONS, 'false')
        connectionProperties.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, 'true')
        connectionProperties.setProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, 'false')
        connectionProperties.setProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN, 'false')
        connectionProperties.setProperty(OracleConnection.DCN_NOTIFY_CHANGELAG, '0')
        def definition = new OracleChangeListenerDefinition(null, Mock(ExecutableMethod), null, null, null, requested)

        when:
        OracleChangeNotificationOptionsValidator.validateConnectionOptions(connectionProperties, definition, 'default')

        then:
        noExceptionThrown()
    }

    void "rejects unsupported reattachment from connection-level DCN options before registration"() {
        given:
        def requested = new Properties()
        requested.setProperty(OracleConnection.NTF_TIMEOUT, '0')
        requested.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, 'true')
        def connection = Mock(OracleConnection)
        def connectionProperties = new Properties()
        connectionProperties.setProperty(OracleConnection.CONNECTION_PROPERTY_DATABASE_CHANGE_NOTIFICATION_OPTIONS,
                'DCN_CLIENT_INIT_CONNECTION=true,DCN_CLIENT_INIT_REGID=42')
        connection.properties >> connectionProperties
        def definition = new OracleChangeListenerDefinition(null, Mock(ExecutableMethod), null, null, null, requested)

        when:
        registrar(Mock(JdbcOperations)).validateConnectionOptions(connection, definition)

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains(OracleConnection.DCN_CLIENT_INIT_REGID)
        0 * connection.registerDatabaseChangeNotification(_, _)
    }

    void "rejects unsupported connection options before creating a registration"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void changed(ChangeEvent<Book>)'
        def requested = new Properties()
        requested.setProperty(OracleConnection.NTF_TIMEOUT, '0')
        requested.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, 'true')
        def connectionProperties = new Properties()
        connectionProperties.setProperty(OracleConnection.CONNECTION_PROPERTY_DATABASE_CHANGE_NOTIFICATION_OPTIONS,
                'DCN_PULL_NOTIFICATIONS=true')
        def definition = new OracleChangeListenerDefinition(null, method, null, 'SELECT * FROM BOOK', null, requested)
        def registrar = registrar(operations)
        def subscription = new OracleChangeNotificationSubscription('default', definition, registrar,
                Mock(Executor), Mock(TaskScheduler), new OracleChangeNotificationTaskTracker())
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.properties >> connectionProperties
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }

        when:
        registrar.createRegistration(subscription)

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains(OracleConnection.DCN_PULL_NOTIFICATIONS)
        0 * oracleConnection.registerDatabaseChangeNotification(_, _)
        0 * connection.createStatement()
    }

    void "validates the effective options reported by the created registration before query association"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def registration = Mock(DatabaseChangeRegistration)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void changed(ChangeEvent<Book>)'
        def requested = new Properties()
        requested.setProperty(OracleConnection.NTF_TIMEOUT, '0')
        requested.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, 'true')
        def effectiveOptions = new Properties()
        effectiveOptions.setProperty(OracleConnection.NTF_TIMEOUT, '60')
        effectiveOptions.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, 'true')
        def definition = new OracleChangeListenerDefinition(null, method, null, 'SELECT * FROM BOOK', null, requested)
        def registrar = registrar(operations)
        def subscription = new OracleChangeNotificationSubscription('default', definition, registrar,
                Mock(Executor), Mock(TaskScheduler), new OracleChangeNotificationTaskTracker())
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.properties >> new Properties()
        registration.getRegId() >> 22L
        registration.getRegistrationOptions() >> effectiveOptions
        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        oracleConnection.registerDatabaseChangeNotification(_ as Properties, _ as DatabaseChangeListener) >> registration

        when:
        registrar.createRegistration(subscription)

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('effective NTF_TIMEOUT [60] conflicts with listener setting [0]')
        1 * oracleConnection.unregisterDatabaseChangeNotification(registration)
        0 * connection.createStatement()
    }

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

    void "attempts to unregister a closed registration after its notification connection fails"() {
        given:
        def operations = Mock(JdbcOperations)
        def registration = Mock(DatabaseChangeRegistration)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        registration.state >> NotificationRegistration.RegistrationState.CLOSED
        connection.unwrap(OracleConnection) >> oracleConnection

        when:
        registrar(operations).unregisterRegistrationAfterFailure(registration)

        then:
        1 * operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback -> callback.call(connection) }
        1 * oracleConnection.unregisterDatabaseChangeNotification(registration)
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
                new OracleChangeNotificationTaskTracker())
    }
}
