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
package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate.nativepostgresql.PostgresStreamingPersonRepository
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.entities.PersonWithIdAndNameDto
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import org.hibernate.Session
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Stream

@MicronautTest(packages = "io.micronaut.data.tck.entities", rollback = false, transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'datasources.default.db-type', value = 'postgres')
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Property(name = 'jpa.default.properties.default-fetch-size', value = '2000')
class HibernatePostgresStreamingSpec extends Specification {

    @Inject
    PostgresStreamingPersonRepository streamingPersonRepository

    @Inject
    TransactionOperations<Session> transactionOperations

    def setup() {
        // Clean before each test to avoid cross-test interference
        streamingPersonRepository.deleteAll()
    }

    def cleanup() {
        streamingPersonRepository.deleteAll()
    }

    void "stream all records without loading into memory (JDBC)"() {
        given:
        long total = 15_000_000L
        seedPersons(total)

        expect: 'stream entities'
        // NOTE: Streaming in Postgres requires setFetchSize(..) and autoCommit false
        transactionOperations.executeRead { status ->
            Session session = status.getConnection()

            long entityCount
            // Execute query with annotated FetchSize
            try (Stream<Person> s = streamingPersonRepository.list()) {
                AtomicLong n = new AtomicLong()
                entityCount = s
                    .peek(p -> {
                        session.detach(p)
                        if ((n.incrementAndGet() % 50_000) == 0) {
                            session.clear()
                        }
                    })
                    .map(p -> 1L)
                    .reduce(0L, Long::sum)
            }

            assert entityCount == total

            // Execute query with default fetch size
            try (Stream<Person> s = streamingPersonRepository.queryAll()) {
                AtomicLong n = new AtomicLong()
                entityCount = s
                        .peek(p -> {
                            session.detach(p)
                            if ((n.incrementAndGet() % 50_000) == 0) {
                                session.clear()
                            }
                        })
                        .map(p -> 1L)
                        .reduce(0L, Long::sum)
            }

            assert entityCount == total


            long projCount
            // Projection stream result with annotated FetchSize
            try (Stream<PersonWithIdAndNameDto> s = streamingPersonRepository.listAllDto()) {
                projCount = s.map(p -> 1L).reduce(0L, Long::sum)
            }
            assert projCount == total

            // Projection stream result with configured fetch size
            try (Stream<PersonWithIdAndNameDto> s = streamingPersonRepository.queryAllDto()) {
                projCount = s.map(p -> 1L).reduce(0L, Long::sum)
            }
            assert projCount == total

            true
        }
    }

    void seedPersons(long count) {
        def sql = """
            INSERT INTO person(id, name, age, enabled)
            SELECT gs + 1 AS id,
                   'Name ' || gs AS name,
                   (gs % 100) AS age,
                   TRUE
            FROM generate_series(0, ?) AS gs
        """.stripIndent()
        // Execute via Hibernate Session to avoid requiring @Connectable on DataSource
        transactionOperations.executeWrite { status ->
            def session = status.getConnection()
            def q = session.createNativeMutationQuery(sql)
            q.setParameter(1, count - 1)
            q.executeUpdate()
            null
        }
    }
}
