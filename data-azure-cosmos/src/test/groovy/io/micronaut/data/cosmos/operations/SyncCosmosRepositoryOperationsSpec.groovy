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
package io.micronaut.data.cosmos.operations

import spock.lang.Specification

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SyncCosmosRepositoryOperationsSpec extends Specification {

    void "close does not shut down injected executor service"() {
        given:
            ExecutorService executorService = Executors.newSingleThreadExecutor()
            SyncCosmosRepositoryOperations operations = newOperations(executorService)

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
            SyncCosmosRepositoryOperations operations = newOperations(null)

        when:
            ExecutorService localExecutorService = operations.async().executor as ExecutorService
            operations.close()

        then:
            localExecutorService.isShutdown()
    }

    private SyncCosmosRepositoryOperations newOperations(ExecutorService executorService) {
        return new SyncCosmosRepositoryOperations(null, executorService)
    }
}
