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
package io.micronaut.data.hibernate.operations

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.transaction.TransactionOperations
import org.hibernate.Session
import org.hibernate.SessionFactory
import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HibernateJpaOperationsSpec extends Specification {

    void "close does not shut down injected executor service"() {
        given:
            ExecutorService executorService = Executors.newSingleThreadExecutor()
            HibernateJpaOperations operations = newOperations(executorService)

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
            HibernateJpaOperations operations = newOperations(null)

        when:
            ExecutorService fallbackExecutor = operations.async().executor as ExecutorService
            operations.close()

        then:
            fallbackExecutor.isShutdown()
    }

    private HibernateJpaOperations newOperations(ExecutorService executorService) {
        JpaConfiguration jpaConfiguration = Mock()
        jpaConfiguration.getProperties() >> [:]
        return new HibernateJpaOperations(
                Mock(SessionFactory),
                Mock(ConnectionOperations<Session>),
                Mock(TransactionOperations<Session>),
                jpaConfiguration,
                executorService,
                Mock(RuntimeEntityRegistry),
                Mock(DataConversionService)
        )
    }
}
