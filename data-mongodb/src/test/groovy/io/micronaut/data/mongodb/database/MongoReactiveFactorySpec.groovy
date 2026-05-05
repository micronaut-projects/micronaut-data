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

import io.micronaut.data.operations.async.AsyncCapableRepository
import spock.lang.Specification

import java.util.concurrent.ExecutorService

class MongoReactiveFactorySpec extends Specification {

    void "close shuts down local executor service"() {
        given:
            def operations = new MongoReactiveFactory().syncOperations(null)

        when:
            ((AsyncCapableRepository) operations).async()
            ExecutorService executorService = executorService(operations)
            ((AutoCloseable) operations).close()

        then:
            executorService.isShutdown()
    }

    private static ExecutorService executorService(Object operations) {
        def field = operations.getClass().getDeclaredField("executorService")
        field.accessible = true
        return (ExecutorService) field.get(operations)
    }
}
