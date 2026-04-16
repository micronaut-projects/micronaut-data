package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.annotation.TransactionPriority
import io.micronaut.data.jdbc.TestResourcesDatabaseTestPropertyProvider
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class OracleTransactionPrioritySpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    TransactionOperations<DataSource> transactionOperations = context.getBean(TransactionOperations)

    @Shared
    ConnectionOperations<DataSource> connectionOperations = context.getBean(ConnectionOperations)

    @Override
    List<String> packages() {
        return ["io.micronaut.data.jdbc.oraclexe.transaction"]
    }

    void "executes Oracle transaction priority code path"() {
        given:
        def mediumPriorityTransaction = new TransactionDefinition() {
            @Override
            TransactionPriority.Level getPriority() {
                return TransactionPriority.Level.MEDIUM
            }
        }
        when:
        Integer inside = transactionOperations.execute(mediumPriorityTransaction) {
            connectionOperations.executeRead { status ->
                status.connection.prepareStatement('select 1 from dual').withCloseable { statement ->
                    statement.executeQuery().withCloseable { resultSet ->
                        return resultSet.next() ? resultSet.getInt(1) : 0
                    }
                }
            }
        }
        Integer outside = connectionOperations.executeRead { status ->
            status.connection.prepareStatement('select 1 from dual').withCloseable { statement ->
                statement.executeQuery().withCloseable { resultSet ->
                    return resultSet.next() ? resultSet.getInt(1) : 0
                }
            }
        }

        then:
        inside == 1
        outside == 1
    }
}
