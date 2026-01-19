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
package io.micronaut.data.tck.tests

import io.micronaut.data.tck.repositories.StreamingPersonReactorRepository
import spock.lang.Specification

abstract class AbstractReactiveStreamingSpec extends Specification  {

    abstract StreamingPersonReactorRepository getStreamingPersonReactorRepository()

    def setup() {
        // Clean before each test to avoid cross-test interference
        streamingPersonReactorRepository.deleteAll().block()
    }

    def cleanup() {
        streamingPersonReactorRepository.deleteAll().block()
    }

    long getDefaultCount() {
        return 15_000_000L
    }

    void "stream all records with backpressure for entity and projection"() {
        given:
        long total = defaultCount
        seedPersons(total)

        when: 'process all entity rows without buffering using default fetch size'
        Long entityCount = streamingPersonReactorRepository.list()
                .limitRate(1)
                .map { 1L }
                .reduce(0L) { a, b -> a + b }
                .block()
        then:
        entityCount == total

        when: 'process all entity rows without buffering using annotated fetch size value'
        entityCount = streamingPersonReactorRepository.queryAll()
                .limitRate(1)
                .map { 1L }
                .reduce(0L) { a, b -> a + b }
                .block()
        then:
        entityCount == total

        when: 'process all projection rows without buffering using default fetch size'
        Long projCount = streamingPersonReactorRepository.listAllDto()
                .limitRate(1)
                .map { 1L }
                .reduce(0L) { a, b -> a + b }
                .block()

        then:
        projCount == total

        when: 'process all projection rows without buffering using annotated fetch size value'
        projCount = streamingPersonReactorRepository.queryAllDto()
                .limitRate(1)
                .map { 1L }
                .reduce(0L) { a, b -> a + b }
                .block()

        then:
        projCount == total
    }

    abstract void seedPersons(long count)

}
