package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.annotation.MicronautTest
import spock.lang.PendingFeature

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest(transactional = false)
class SqlServerSequenceSpec extends AbstractSqlServerSpec {
    @Inject TestSequenceRepo testSequenceRepo
    @Inject DataSource dataSource

    @PendingFeature(reason = "Currently SQL server doesn't support return generated keys for sequences. See https://github.com/microsoft/mssql-jdbc/issues/656")
    void "test SQL server sequence handling"() {
        when:
        def test = testSequenceRepo.save(new TestSequenceId(name: "Fred"))

        then:
        test.id
        testSequenceRepo.findById(test.id).isPresent()

        when:
        def currentValue = dataSource.connection.withCloseable {
            it.prepareStatement("select last_value from test_sequence_id_seq").withCloseable { ps ->
                ps.executeQuery().withCloseable { rs ->
                    rs.next()
                    return rs.getLong(1)
                }
            }
        }

        def name = dataSource.connection.withCloseable {
            it.prepareStatement("select \"name\" from test_sequence_id").withCloseable { ps ->
                ps.executeQuery().withCloseable { rs ->
                    if (rs.next()) {
                        return rs.getString(1)
                    }
                }
            }
        }

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

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface TestSequenceRepo extends CrudRepository<TestSequenceId, Long> {}
