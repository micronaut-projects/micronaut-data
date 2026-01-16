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
package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.entities.PersonWithIdAndNameDto
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.util.stream.Stream

@Ignore(value = "Fix as part of https://github.com/micronaut-projects/micronaut-data/issues/3679")
class PostgresStreamingSpec extends Specification implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    JdbcOperations jdbcOperations = context.getBean(JdbcOperations)

    @Shared
    TransactionOperations<DataSource> txOperations = context.getBean(TransactionOperations)

    @Shared
    PostgresStreamingPersonRepository repository = context.getBean(PostgresStreamingPersonRepository)

    def setup() {
        // Clean before each test to avoid cross-test interference
        repository.deleteAll()
    }

    void "stream all records without loading into memory (JDBC)"() {
        given:
        long total = 15_000_000L
        seedPersons(total)

        expect: 'stream entities'
        // NOTE: Streaming in Postgres requires setFetchSize(..) and autoCommit false
        txOperations.executeRead {
            long entityCount
            try (Stream<Person> s = repository.list()) {
                entityCount = s.map(p -> 1L).reduce(0L, Long::sum)
            }

            assert entityCount == total

            long projCount
            try (Stream<PersonWithIdAndNameDto> s = repository.listAll()) {
                projCount = s.map(p -> 1L).reduce(0L, Long::sum)
            }
            assert projCount == total
            true
        }
    }

    private void seedPersons(long count) {
        jdbcOperations.execute { connection -> {
            jdbcOperations.prepareStatement("""
                INSERT INTO person(name, age, enabled)
                SELECT 'Name ' || gs AS name,
                       (gs % 100) AS age,
                       TRUE
                FROM generate_series(0, ?) AS gs
            """.stripIndent(), ps -> {
                ps.setLong(1, count - 1)
                ps.executeUpdate()
                return null
            })
        }}

    }
}
