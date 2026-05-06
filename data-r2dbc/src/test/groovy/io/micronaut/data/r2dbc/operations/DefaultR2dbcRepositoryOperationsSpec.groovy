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
package io.micronaut.data.r2dbc.operations

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.reactive.ReactorConnectionOperations
import io.micronaut.data.model.runtime.AttributeConverterRegistry
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.date.DateTimeProvider
import io.micronaut.data.runtime.event.EntityEventRegistry
import io.micronaut.data.runtime.operations.internal.sql.SqlJsonColumnMapperProvider
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import jakarta.inject.Provider
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultR2dbcRepositoryOperationsSpec extends Specification {

    ApplicationContext context

    void cleanup() {
        context?.close()
    }

    void "close does not shut down injected executor service"() {
        given:
            ExecutorService executorService = Executors.newSingleThreadExecutor()
        DefaultR2dbcRepositoryOperations operations = newOperations(executorService)

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
            DefaultR2dbcRepositoryOperations operations = newOperations(null)

        when:
            ExecutorService fallbackExecutor = operations.async().executor as ExecutorService
            operations.close()

        then:
            fallbackExecutor.isShutdown()
    }

    private DefaultR2dbcRepositoryOperations newOperations(ExecutorService executorService) {
        context = ApplicationContext.run()
        ConnectionFactory connectionFactory = Mock()
        return new DefaultR2dbcRepositoryOperations(
                "default",
                connectionFactory,
                Mock(DateTimeProvider),
                runtimeEntityRegistry(),
                context,
                executorService,
                Mock(DataConversionService),
                Mock(AttributeConverterRegistry),
                null,
                Mock(R2dbcSchemaHandler),
                new DataR2dbcConfiguration("default", connectionFactory, Mock(Provider)),
                null,
                new SqlJsonColumnMapperProvider<Row>(null, [], []),
                [],
                Mock(R2dbcReactorTransactionOperations),
                Mock(ReactorConnectionOperations<Connection>)
        )
    }

    private RuntimeEntityRegistry runtimeEntityRegistry() {
        RuntimeEntityRegistry runtimeEntityRegistry = Mock()
        runtimeEntityRegistry.getEntityEventListener() >> Mock(EntityEventRegistry)
        runtimeEntityRegistry.getApplicationContext() >> context
        return runtimeEntityRegistry
    }
}
