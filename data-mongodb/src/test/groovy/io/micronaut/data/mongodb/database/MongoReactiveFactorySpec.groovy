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
package io.micronaut.data.mongodb.database

import com.mongodb.reactivestreams.client.MongoClient
import io.micronaut.context.BeanContext
import io.micronaut.data.connection.reactive.ReactorConnectionOperations
import io.micronaut.data.model.runtime.AttributeConverterRegistry
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.mongodb.operations.DefaultReactiveMongoRepositoryOperations
import io.micronaut.data.mongodb.operations.MongoCollectionNameProvider
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider
import io.micronaut.data.operations.async.AsyncCapableRepository
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.date.DateTimeProvider
import io.micronaut.data.runtime.event.EntityEventRegistry
import spock.lang.Specification

class MongoReactiveFactorySpec extends Specification {

    void "close shuts down local executor service"() {
        given:
            def operations = new MongoReactiveFactory().syncOperations(newReactiveOperations())

        when:
            def executorService = ((AsyncCapableRepository) operations).async().executor
            ((AutoCloseable) operations).close()

        then:
            executorService.isShutdown()
    }

    private DefaultReactiveMongoRepositoryOperations newReactiveOperations() {
        BeanContext beanContext = Mock()
        beanContext.getBean(MongoDatabaseNameProvider, _) >> Mock(MongoDatabaseNameProvider)
        def constructor = DefaultReactiveMongoRepositoryOperations.getDeclaredConstructor(
                String,
                BeanContext,
                DateTimeProvider,
                RuntimeEntityRegistry,
                DataConversionService,
                AttributeConverterRegistry,
                MongoClient,
                MongoCollectionNameProvider,
                ReactorConnectionOperations
        )
        constructor.accessible = true
        return constructor.newInstance(
                "Primary",
                beanContext,
                Mock(DateTimeProvider),
                runtimeEntityRegistry(),
                Mock(DataConversionService),
                Mock(AttributeConverterRegistry),
                Mock(MongoClient),
                Mock(MongoCollectionNameProvider),
                Mock(ReactorConnectionOperations)
        )
    }

    private RuntimeEntityRegistry runtimeEntityRegistry() {
        RuntimeEntityRegistry runtimeEntityRegistry = Mock()
        runtimeEntityRegistry.getEntityEventListener() >> Mock(EntityEventRegistry)
        return runtimeEntityRegistry
    }
}
