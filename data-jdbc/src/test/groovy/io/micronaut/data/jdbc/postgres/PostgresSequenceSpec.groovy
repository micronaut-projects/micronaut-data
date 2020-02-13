package io.micronaut.data.jdbc.postgres

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.transaction.TransactionStatus

import javax.inject.Inject
import javax.sql.DataSource
import java.sql.Connection

@MicronautTest(transactional = false)
class PostgresSequenceSpec extends AbstractPostgresSpec {

    @Inject TestSequenceRepo testSequenceRepo
    @Inject DataSource dataSource
    @Inject SynchronousTransactionManager<Connection> transactionManager

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