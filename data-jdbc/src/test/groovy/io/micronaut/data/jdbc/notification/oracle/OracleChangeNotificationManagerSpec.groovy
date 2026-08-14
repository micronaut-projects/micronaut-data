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
import oracle.jdbc.OracleConnection
import oracle.jdbc.OracleStatement
import oracle.jdbc.dcn.DatabaseChangeRegistration
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.Executor

class OracleChangeNotificationManagerSpec extends Specification {

    void "rolls back earlier registrations when startup registration fails"() {
        given:
        def operations = Mock(JdbcOperations)
        def connection = Mock(Connection)
        def oracleConnection = Mock(OracleConnection)
        def firstRegistration = Mock(DatabaseChangeRegistration)
        def secondRegistration = Mock(DatabaseChangeRegistration)
        def firstStatement = Mock(Statement)
        def secondStatement = Mock(Statement)
        def firstOracleStatement = Mock(OracleStatement)
        def secondOracleStatement = Mock(OracleStatement)
        def resultSet = Mock(ResultSet)
        def firstMethod = Mock(ExecutableMethod)
        def secondMethod = Mock(ExecutableMethod)
        firstMethod.getDescription(true) >> "void firstListener(ChangeEvent<Book>)"
        secondMethod.getDescription(true) >> "void failingListener(ChangeEvent<Book>)"
        Executor executor = { Runnable command -> command.run() } as Executor
        def manager = new OracleChangeNotificationManager("inventory", operations, Mock(BeanContext), executor)
        manager.addDefinition(definition("SELECT * FROM BOOK", firstMethod))
        manager.addDefinition(definition("INVALID SQL", secondMethod))

        operations.execute(_ as ConnectionCallback) >> { ConnectionCallback<?> callback ->
            try {
                return callback.call(connection)
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Callback: ${e.message}", e)
            }
        }
        connection.unwrap(OracleConnection) >> oracleConnection
        oracleConnection.registerDatabaseChangeNotification(_ as Properties) >>> [firstRegistration, secondRegistration]
        connection.createStatement() >>> [firstStatement, secondStatement]
        firstStatement.unwrap(OracleStatement) >> firstOracleStatement
        secondStatement.unwrap(OracleStatement) >> secondOracleStatement
        firstStatement.executeQuery("SELECT * FROM BOOK") >> resultSet
        secondStatement.executeQuery("INVALID SQL") >> { throw new SQLException("Invalid registration query") }

        when:
        manager.start()

        then:
        def exception = thrown(DataAccessException)
        exception.message == "Unable to register Oracle query notification for datasource [inventory] and listener method [void failingListener(ChangeEvent<Book>)]"
        exception.cause instanceof DataAccessException
        exception.cause.cause instanceof SQLException
        exception.cause.cause.message == "Invalid registration query"
        1 * oracleConnection.unregisterDatabaseChangeNotification(secondRegistration)
        1 * oracleConnection.unregisterDatabaseChangeNotification(firstRegistration)
    }

    private static OracleChangeListenerDefinition definition(String query, ExecutableMethod<?, ?> method) {
        return new OracleChangeListenerDefinition(null, method, "BOOK", query, null, new Properties())
    }
}
