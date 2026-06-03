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

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.AttributeConverterRegistry
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.date.DateTimeProvider
import io.micronaut.data.runtime.event.EntityEventRegistry
import io.micronaut.data.runtime.operations.internal.sql.SqlJsonColumnMapperProvider
import io.micronaut.transaction.TransactionOperations
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException
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

    void "batch capability metadata failures are not cached"() {
        given:
            DefaultJdbcRepositoryOperations operations = newOperations(null)
            DatabaseMetaData metaData = Mock {
                getDatabaseProductName() >> "MySQL"
                getDriverName() >> "MySQL Connector/J"
                supportsBatchUpdates() >> true
                supportsGetGeneratedKeys() >> true
            }
            Connection connection = Mock()
            RuntimePersistentProperty<?> identity = Mock {
                isGenerated() >> true
            }
            RuntimePersistentEntity<?> persistentEntity = Mock {
                hasIdentity() >> true
                getIdentity() >> identity
            }
            def operationContext = new DefaultJdbcRepositoryOperations.JdbcOperationContext(
                    AnnotationMetadata.EMPTY_METADATA,
                    null,
                    Object,
                    Dialect.MYSQL,
                    connection
            )

        when:
            boolean firstAttempt = operations.isSupportsBatchInsert(operationContext, persistentEntity)

        then:
            1 * connection.getMetaData() >> { throw new SQLException("temporary metadata failure") }
            !firstAttempt

        when:
            boolean secondAttempt = operations.isSupportsBatchInsert(operationContext, persistentEntity)

        then:
            1 * connection.getMetaData() >> metaData
            secondAttempt
    }

    private DefaultJdbcRepositoryOperations newOperations(ExecutorService executorService) {
        context = ApplicationContext.run()
        return new DefaultJdbcRepositoryOperations(
                "default",
                new DataJdbcConfiguration("default"),
                Mock(DataSource),
                Mock(ConnectionOperations<Connection>),
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
