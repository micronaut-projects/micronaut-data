/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.transaction.TransactionStatus
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

class PostgresSequenceSpec extends Specification implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    TestSequenceRepo testSequenceRepo = applicationContext.getBean(TestSequenceRepo)

    @Shared
    DataSource dataSource = applicationContext.getBean(DataSource)

    @Shared
    SynchronousTransactionManager<Connection> transactionManager = applicationContext.getBean(SynchronousTransactionManager)

    void "test postgres sequence handling"() {
        when:
        def test = testSequenceRepo.save(new TestSequenceId(name: "Fred"))

        then:
        test.id
        testSequenceRepo.findById(test.id).isPresent()

        when:
        def currentValue = transactionManager.executeRead({ TransactionStatus status ->
            Connection it = status.connection
            it.prepareStatement("select last_value from test_sequence_id_seq").withCloseable { ps ->
                ps.executeQuery().withCloseable { rs ->
                    rs.next()
                    return rs.getLong(1)
                }
            }
        })

        def name = transactionManager.executeRead({ TransactionStatus status ->
            Connection it = status.connection
            it.prepareStatement("select \"name\" from test_sequence_id").withCloseable { ps ->
                ps.executeQuery().withCloseable { rs ->
                    if (rs.next()) {
                        return rs.getString(1)
                    }
                }
            }
        })

        then:
        currentValue == test.id
        name == "Fred"
    }

}

@MappedEntity
class TestSequenceId {

    @GeneratedValue(GeneratedValue.Type.SEQUENCE)
    @Id
    Long id

    String name
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface TestSequenceRepo extends CrudRepository<TestSequenceId, Long> {}