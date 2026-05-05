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
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler
import io.micronaut.data.model.runtime.AttributeConverterRegistry
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.date.DateTimeProvider
import io.micronaut.data.runtime.event.EntityEventRegistry
import io.micronaut.data.runtime.operations.internal.sql.SqlJsonColumnMapperProvider
import io.micronaut.transaction.TransactionOperations
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.ResultSet
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
            operations.async()
            ExecutorService localExecutorService = localExecutorService(operations)
            operations.close()

        then:
            localExecutorService.isShutdown()
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
                new SqlJsonColumnMapperProvider<ResultSet>(null, [], []),
                []
        )
    }

    private RuntimeEntityRegistry runtimeEntityRegistry() {
        RuntimeEntityRegistry runtimeEntityRegistry = Mock()
        runtimeEntityRegistry.getEntityEventListener() >> Mock(EntityEventRegistry)
        runtimeEntityRegistry.getApplicationContext() >> context
        return runtimeEntityRegistry
    }

    private static ExecutorService localExecutorService(DefaultJdbcRepositoryOperations operations) {
        def field = DefaultJdbcRepositoryOperations.getDeclaredField("localExecutorService")
        field.accessible = true
        return (ExecutorService) field.get(operations)
    }
}
