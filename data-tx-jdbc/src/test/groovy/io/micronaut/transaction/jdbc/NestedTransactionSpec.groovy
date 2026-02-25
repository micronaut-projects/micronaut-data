package io.micronaut.transaction.jdbc

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Issue('https://github.com/micronaut-projects/micronaut-data/issues/3334')
class NestedTransactionSpec extends Specification {

    @Inject
    TransactionOperations<Connection> transactionManager

    @Inject
    DataSource dataSource

    private static final TransactionDefinition NESTED =
        TransactionDefinition.of(TransactionDefinition.Propagation.NESTED)

    void setup() {
        transactionManager.executeWrite({ status ->
            def connection = dataSource.getConnection()
            connection.prepareStatement("drop table test_nested if exists").execute()
            connection.prepareStatement(
                "create table test_nested (id bigint not null auto_increment, name varchar(255), primary key (id))"
            ).execute()
            return null
        })
    }

    void "nested transaction rolls back without affecting outer transaction"() {
        when:
        transactionManager.executeWrite({ status ->
            insertRow("A")
            try {
                transactionManager.execute(NESTED, { nestedStatus ->
                    insertRow("B")
                    throw new RuntimeException("nested failure")
                })
            } catch (RuntimeException ignored) {
                // Outer transaction catches and continues
            }
            return null
        })

        then:
        getNames() == ["A"]
    }

    void "nested transaction commits independently"() {
        when:
        transactionManager.executeWrite({ status ->
            insertRow("A")
            transactionManager.execute(NESTED, { nestedStatus ->
                insertRow("B")
                return null
            })
            return null
        })

        then:
        getNames().sort() == ["A", "B"]
    }

    void "multiple nested transactions with selective rollback"() {
        when:
        transactionManager.executeWrite({ status ->
            insertRow("A")
            transactionManager.execute(NESTED, { nestedStatus ->
                insertRow("B")
                return null
            })
            try {
                transactionManager.execute(NESTED, { nestedStatus ->
                    insertRow("C")
                    throw new RuntimeException("second nested failure")
                })
            } catch (RuntimeException ignored) {
                // Catch the second nested failure
            }
            return null
        })

        then:
        getNames().sort() == ["A", "B"]
    }

    void "nested with setRollbackOnly rolls back only the savepoint"() {
        when:
        transactionManager.executeWrite({ status ->
            insertRow("A")
            transactionManager.execute(NESTED, { nestedStatus ->
                insertRow("B")
                nestedStatus.setRollbackOnly()
                return null
            })
            return null
        })

        then:
        getNames() == ["A"]
    }

    void "NESTED without existing transaction starts a new transaction"() {
        when:
        transactionManager.execute(NESTED, { status ->
            insertRow("A")
            return null
        })

        then:
        getNames() == ["A"]
    }

    void "uncaught nested exception rolls back both nested and outer"() {
        when:
        transactionManager.executeWrite({ status ->
            insertRow("A")
            transactionManager.execute(NESTED, { nestedStatus ->
                insertRow("B")
                throw new RuntimeException("uncaught nested failure")
            })
            return null
        })

        then:
        thrown(RuntimeException)
        getNames() == []
    }

    void "nested within nested creates independent savepoints"() {
        when:
        transactionManager.executeWrite({ status ->
            insertRow("A")
            transactionManager.execute(NESTED, { outerNested ->
                insertRow("B")
                try {
                    transactionManager.execute(NESTED, { innerNested ->
                        insertRow("C")
                        throw new RuntimeException("inner nested failure")
                    })
                } catch (RuntimeException ignored) {
                    // Only the innermost nested rolls back
                }
                return null
            })
            return null
        })

        then:
        getNames().sort() == ["A", "B"]
    }

    private void insertRow(String name) {
        def connection = dataSource.getConnection()
        try (def ps = connection.prepareStatement("insert into test_nested (name) values(?)")) {
            ps.setString(1, name)
            ps.execute()
        }
    }

    private List<String> getNames() {
        return transactionManager.executeRead({ status ->
            def connection = dataSource.getConnection()
            try (def ps = connection.prepareStatement("select name from test_nested order by name")) {
                try (def rs = ps.executeQuery()) {
                    def names = []
                    while (rs.next()) {
                        names << rs.getString("name")
                    }
                    return names
                }
            }
        })
    }
}
