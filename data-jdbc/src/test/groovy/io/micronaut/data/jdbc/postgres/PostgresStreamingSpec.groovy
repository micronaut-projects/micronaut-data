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
import io.micronaut.data.tck.repositories.StreamingPersonRepository
import io.micronaut.data.tck.tests.AbstractStreamingSpec
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared

import javax.sql.DataSource

class PostgresStreamingSpec extends AbstractStreamingSpec implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    JdbcOperations jdbcOperations = context.getBean(JdbcOperations)

    @Override
    TransactionOperations<DataSource> getTxOperations() {
        return context.getBean(TransactionOperations)
    }

    @Override
    StreamingPersonRepository getStreamingPersonRepository() {
        return context.getBean(PostgresStreamingPersonRepository)
    }

    @Override
    void seedPersons(long count) {
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
