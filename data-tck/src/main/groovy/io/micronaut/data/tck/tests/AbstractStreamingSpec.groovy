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

import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.entities.PersonWithIdAndNameDto
import io.micronaut.data.tck.repositories.StreamingPersonRepository
import io.micronaut.transaction.TransactionOperations
import spock.lang.Specification

import javax.sql.DataSource
import java.util.stream.Stream

abstract class AbstractStreamingSpec extends Specification  {

    abstract TransactionOperations<DataSource> getTxOperations()

    abstract StreamingPersonRepository getStreamingPersonRepository()

    long getDefaultCount() {
        return 15_000_000L
    }

    def setup() {
        // Clean before each test to avoid cross-test interference
        streamingPersonRepository.deleteAll()
    }

    def cleanup() {
        streamingPersonRepository.deleteAll()
    }

    void "stream all records without loading into memory (JDBC)"() {
        given:
        long total = defaultCount
        seedPersons(total)

        expect: 'stream entities'
        // NOTE: Streaming in Postgres requires setFetchSize(..) and autoCommit false
        txOperations.executeRead {
            long entityCount
            try (Stream<Person> s = streamingPersonRepository.list()) {
                entityCount = s.map(p -> 1L).reduce(0L, Long::sum)
            }

            assert entityCount == total

            try (Stream<Person> s = streamingPersonRepository.queryAll()) {
                entityCount = s.map(p -> 1L).reduce(0L, Long::sum)
            }

            assert entityCount == total

            long projCount
            try (Stream<PersonWithIdAndNameDto> s = streamingPersonRepository.listAllDto()) {
                projCount = s.map(p -> 1L).reduce(0L, Long::sum)
            }
            assert projCount == total

            try (Stream<PersonWithIdAndNameDto> s = streamingPersonRepository.queryAllDto()) {
                projCount = s.map(p -> 1L).reduce(0L, Long::sum)
            }
            assert projCount == total
            true
        }
    }

    abstract void seedPersons(long count)
}
