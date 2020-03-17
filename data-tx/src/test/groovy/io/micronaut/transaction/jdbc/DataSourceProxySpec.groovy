package io.micronaut.transaction.jdbc

import io.micronaut.context.annotation.Property
import io.micronaut.jdbc.DataSourceResolver
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.transaction.TransactionCallback
import io.micronaut.transaction.TransactionStatus
import io.micronaut.transaction.exceptions.NoTransactionException
import spock.lang.Specification

import javax.inject.Inject
import javax.sql.DataSource
import java.sql.Connection

@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
class DataSourceProxySpec extends Specification {

    @Inject
    DataSource dataSource

    @Inject
    DataSourceResolver dataSourceResolver

    @Inject
    SynchronousTransactionManager<Connection> transactionManager

    @Inject
    TestService testService

    void "test rollback"() {
        given:
        testService.init()
        when:
        transactionManager.executeWrite({ TransactionStatus status ->
            def connection = dataSource.getConnection()
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(1000, 'The Stand')")
            ps1.execute()
            ps1.close()
        } as TransactionCallback)

        then:
        transactionManager.executeRead({ TransactionStatus status ->
            getCount()
        }) == 1

        when:
        transactionManager.executeWrite({ TransactionStatus status ->
            def connection = dataSource.getConnection()
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(200, 'The Shining')")
            ps1.execute()
            ps1.close()
            throw new RuntimeException("Bad things happened")
        } as TransactionCallback)

        then:
        def e = thrown(RuntimeException)
        e.message == 'Bad things happened'
        transactionManager.executeRead({ TransactionStatus status ->
            getCount()
        }) == 1

    }

    void "test outside of transaction"() {
        when:
        def connection = dataSource.getConnection()
        connection.prepareStatement("select 1")

        then:
        thrown NoTransactionException

        when:
        connection = dataSourceResolver.resolve(dataSource).getConnection()
        def ps = connection.prepareStatement("select 1")
        def success = ps.execute();
        ps.close();

        then:
        success
    }

    int getCount() {
        def connection = dataSource.getConnection()
        def ps2 = connection.prepareStatement("select count(*) as count from book")
        def rs = ps2.executeQuery()
        try {
            rs.next()
            rs.getInt("count")
        } finally {
            ps2.close()
            rs.close()
        }
    }
}
