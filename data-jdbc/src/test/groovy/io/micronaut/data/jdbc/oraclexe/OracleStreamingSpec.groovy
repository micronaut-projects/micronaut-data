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
package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.tck.repositories.StreamingPersonRepository
import io.micronaut.data.tck.tests.AbstractStreamingSpec
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared

import javax.sql.DataSource

class OracleStreamingSpec extends AbstractStreamingSpec implements OracleTestPropertyProvider {

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
        return context.getBean(OracleStreamingPersonRepository)
    }

    @Override
    long getDefaultCount() {
        return 1_500_000
    }

    @Override
    void seedPersons(long count) {
        long step = 500_000L
        long full = count / step
        long remainder = count % step
        txOperations.executeWrite {
            for (int i = 0; i < full; i++) {
                long offset = i * step
                jdbcOperations.execute { connection -> {
                    jdbcOperations.prepareStatement("""
                    INSERT INTO person(id, name, age, enabled)
                    SELECT (LEVEL - 1 + ?), 'Name ' || (LEVEL - 1 + ?) AS name,
                           MOD((LEVEL - 1 + ?), 100) AS age,
                           1
                    FROM DUAL
                    CONNECT BY LEVEL <= ?
                """.stripIndent(), ps -> {
                        ps.setLong(1, offset)
                        ps.setLong(2, offset)
                        ps.setLong(3, offset)
                        ps.setLong(4, step)
                        ps.executeUpdate()
                        return null
                    })
                }}
            }
            if (remainder > 0) {
                long offset = full * step
                jdbcOperations.execute { connection -> {
                    jdbcOperations.prepareStatement("""
                    INSERT INTO person(id, name, age, enabled)
                    SELECT (LEVEL - 1 + ?), 'Name ' || (LEVEL - 1 + ?) AS name,
                           MOD((LEVEL - 1 + ?), 100) AS age,
                           1
                    FROM DUAL
                    CONNECT BY LEVEL <= ?
                """.stripIndent(), ps -> {
                        ps.setLong(1, offset)
                        ps.setLong(2, offset)
                        ps.setLong(3, offset)
                        ps.setLong(4, remainder)
                        ps.executeUpdate()
                        return null
                    })
                }}
            }
            true
        }
    }
}
