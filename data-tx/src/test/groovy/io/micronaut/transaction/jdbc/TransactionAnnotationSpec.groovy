package io.micronaut.transaction.jdbc

import io.micronaut.context.BeanDefinitionRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification
import spock.lang.Stepwise

import javax.inject.Inject
import javax.inject.Singleton
import javax.transaction.Transactional
import java.sql.Connection

@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Stepwise
class TransactionAnnotationSpec extends Specification {

    @Inject TestService testService
    @Inject BeanDefinitionRegistry registry

    void "test transactional annotation handling"() {
        when:"an insert is performed in a transaction"
        testService.insertTransctionally()

        then:"The insert worked"
        testService.readTransactionally() == 1

        when:"A transaction is rolled back"
        testService.insertAndRollback()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Bad things happened'
        testService.readTransactionally() == 1


        when:"A transaction is rolled back"
        testService.insertAndRollbackChecked()

        then:
        def e2 = thrown(Exception)
        e2.message == 'Bad things happened'
        testService.readTransactionally() == 1
    }

    @Singleton
    static class TestService {
        @Inject Connection connection

        @Transactional
        @EventListener
        void init(StartupEvent startupEvent) {
                connection.prepareStatement("drop table book if exists").execute()
                connection.prepareStatement("create table book (id bigint not null auto_increment, pages integer not null, title varchar(255), primary key (id))").execute()

        }

         @Transactional
        void insertTransctionally() {
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(100, 'The Stand')")
            ps1.execute()
            ps1.close()
        }

        @Transactional
        void insertAndRollback() {
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(200, 'The Shining')")
            ps1.execute()
            ps1.close()
            throw new RuntimeException("Bad things happened")
        }


        @Transactional
        void insertAndRollbackChecked() {
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(200, 'The Shining')")
            ps1.execute()
            ps1.close()
            throw new Exception("Bad things happened")
        }


        @Transactional
        int readTransactionally() {
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
}
