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
package io.micronaut.data.runtime.operations.internal

import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ExecutorServiceResolverSpec extends Specification {

    void "concurrent get creates one local executor service"() {
        given:
            ExecutorServiceResolver resolver = new ExecutorServiceResolver(null)
            CountDownLatch ready = new CountDownLatch(8)
            CountDownLatch start = new CountDownLatch(1)
            ExecutorService callers = Executors.newFixedThreadPool(8)

        when:
            List<Future<ExecutorService>> futures = (1..8).collect {
                callers.submit({
                    ready.countDown()
                    start.await()
                    resolver.get()
                })
            }
            assert ready.await(5, TimeUnit.SECONDS)
            start.countDown()
            List<ExecutorService> executorServices = futures.collect { it.get(5, TimeUnit.SECONDS) }
            resolver.close()

        then:
            executorServices.toSet().size() == 1
            executorServices[0].isShutdown()

        cleanup:
            callers.shutdownNow()
    }
}
