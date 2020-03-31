package io.micronaut.transaction.jdbc

import io.micronaut.context.BeanDefinitionRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.transaction.annotation.TransactionalEventListener
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
        given:
        testService.init()
        
        when:"an insert is performed in a transaction"
        testService.insertWithTransaction()

        then:"The insert worked"
        testService.lastEvent?.title == "The Stand"
        testService.readTransactionally() == 1

        when:"A transaction is rolled back"
        testService.lastEvent = null
        testService.insertAndRollback()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Bad things happened'
        testService.lastEvent == null
        testService.readTransactionally() == 1


        when:"A transaction is rolled back"
        testService.insertAndRollbackChecked()

        then:
        def e2 = thrown(Exception)
        e2.message == 'Bad things happened'
        testService.lastEvent == null
        testService.readTransactionally() == 1

        when:"A transaction is rolled back but the exception ignored"
        testService.insertAndRollbackDontRollbackOn()

        then:
        thrown(IOException)
        testService.readTransactionally() == 2
        testService.lastEvent


        when:"Test that connections are never exhausted"
        int i = 0
        200.times { i += testService.readTransactionally() }

        then:"We're ok at it completed"
        i == 400
    }

    @Singleton
    static class TestService {
        @Inject Connection connection
        @Inject ApplicationEventPublisher eventPublisher
        NewBookEvent lastEvent

        @Transactional
        void init() {
                connection.prepareStatement("drop table book if exists").execute()
                connection.prepareStatement("create table book (id bigint not null auto_increment, pages integer not null, title varchar(255), primary key (id))").execute()

        }

         @Transactional
        void insertWithTransaction() {
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(100, 'The Stand')")
            ps1.execute()
            ps1.close()
            eventPublisher.publishEvent(new NewBookEvent("The Stand"))
        }

        @Transactional
        void insertAndRollback() {
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(200, 'The Shining')")
            ps1.execute()
            ps1.close()
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new RuntimeException("Bad things happened")
        }


        @Transactional
        void insertAndRollbackChecked() {
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(200, 'The Shining')")
            ps1.execute()
            ps1.close()
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new Exception("Bad things happened")
        }

        @Transactional(dontRollbackOn = IOException)
        void insertAndRollbackDontRollbackOn() {
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(200, 'The Shining')")
            ps1.execute()
            ps1.close()
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new IOException("Bad things happened")
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

        @TransactionalEventListener
        void afterCommit(NewBookEvent event) {
            lastEvent = event
        }
    }

    static class NewBookEvent {
        final String title

        NewBookEvent(String title) {
            this.title = title
        }
    }
}
