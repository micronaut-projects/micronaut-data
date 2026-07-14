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
package io.micronaut.data.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
import io.micronaut.data.model.runtime.AttributeConverterRegistry
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.date.DateTimeProvider
import io.micronaut.data.runtime.event.EntityEventRegistry
import io.micronaut.data.runtime.operations.internal.sql.SqlJsonColumnMapperProvider
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery
import io.micronaut.transaction.TransactionOperations
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultJdbcRepositoryOperationsSpec extends Specification {

    ApplicationContext context

    void cleanup() {
        context?.close()
    }

    void "close does not shut down injected executor service"() {
        given:
            ExecutorService executorService = Executors.newSingleThreadExecutor()
        DefaultJdbcRepositoryOperations operations = newOperations(executorService)

        when:
            operations.async()
            operations.close()

        then:
            !executorService.isShutdown()

        cleanup:
            executorService.shutdownNow()
    }

    void "close shuts down local executor service"() {
        given:
            DefaultJdbcRepositoryOperations operations = newOperations(null)

        when:
            ExecutorService fallbackExecutor = operations.async().executor as ExecutorService
            operations.close()

        then:
            fallbackExecutor.isShutdown()
    }

    void "binds datasource dialect options"() {
        given:
            context = ApplicationContext.run([
                "datasources.default.dialect": "ORACLE",
                "datasources.default.dialect-options.version": "23.1",
                "datasources.default.dialect-options.validate-version": false,
                "datasources.default.enabled": false
            ])

        when:
            def configuration = context.getBean(DataJdbcConfiguration)

        then:
            configuration.dialect == Dialect.ORACLE
            configuration.dialectOptions.version == "23.1"
            !configuration.dialectOptions.validateVersion
    }

    void "enables datasource target version validation by default"() {
        expect:
            new DataJdbcConfiguration("default").dialectOptions.validateVersion
    }

    void "ignores malformed target version metadata for JDBC diagnostics"() {
        given:
            Connection connection = Mock()
            ConnectionStatus<Connection> connectionStatus = Mock()
            ConnectionOperations<Connection> connectionOperations = Mock()
            PreparedStatement statement = Mock()
            DefaultJdbcRepositoryOperations operations = newOperations(null, connectionOperations)
            SqlPreparedQuery<Object, Number> query = Mock()
            query.dialect >> Dialect.ORACLE
            query.dialectVersion >> "23.1.0.1"
            query.annotationMetadata >> io.micronaut.core.annotation.AnnotationMetadata.EMPTY_METADATA
            query.query >> "UPDATE test SET active = 1"
            query.optimisticLock >> false
            connectionOperations.execute(_, _) >> { _, callback -> callback.apply(connectionStatus) }
            connectionStatus.connection >> connection
            connection.prepareStatement("UPDATE test SET active = 1") >> statement
            statement.executeUpdate() >> 0

        when:
            Optional<Number> result = operations.executeUpdate(query)

        then:
            result == Optional.of(0)
            0 * connection.getMetaData()
    }

    private DefaultJdbcRepositoryOperations newOperations(ExecutorService executorService) {
        newOperations(executorService, Mock(ConnectionOperations<Connection>))
    }

    private DefaultJdbcRepositoryOperations newOperations(ExecutorService executorService,
                                                           ConnectionOperations<Connection> connectionOperations) {
        context = ApplicationContext.run()
        return new DefaultJdbcRepositoryOperations(
                "default",
                new DataJdbcConfiguration("default"),
                Mock(DataSource),
                connectionOperations,
                Mock(TransactionOperations<Connection>),
                executorService,
                context,
                Mock(DateTimeProvider),
                runtimeEntityRegistry(),
                Mock(DataConversionService),
                Mock(AttributeConverterRegistry),
                null,
                Mock(JdbcSchemaHandler),
                null,
                context.getBean(SqlJsonColumnMapperProvider),
                Mock(DatabaseConversionContextFactory),
                []
        )
    }

    private RuntimeEntityRegistry runtimeEntityRegistry() {
        RuntimeEntityRegistry runtimeEntityRegistry = Mock()
        runtimeEntityRegistry.getEntityEventListener() >> Mock(EntityEventRegistry)
        runtimeEntityRegistry.getApplicationContext() >> context
        return runtimeEntityRegistry
    }
}
