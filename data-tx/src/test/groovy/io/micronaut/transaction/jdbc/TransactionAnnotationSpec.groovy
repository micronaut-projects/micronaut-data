package io.micronaut.transaction.jdbc

import io.micronaut.context.BeanDefinitionRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.scheduling.annotation.Async
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Stepwise
import spock.util.concurrent.AsyncConditions

import javax.transaction.Transactional
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger

@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Stepwise
class TransactionAnnotationSpec extends Specification {

    @Inject TestService testService
    @Inject BeanDefinitionRegistry registry

    AsyncConditions asyncConditions = new AsyncConditions()

    void "test transactional annotation handling"() {
        given:
            testService.init()

        when:"an insert is performed in a transaction"
            testService.insertWithTransaction()

        then:"The insert worked"
            testService.lastEvent?.title == "The Stand"
            testService.readTransactionally() == 1
            testService.commitCount.get() + testService.rollbackCount.get() == testService.completionCount.get()

        when:"A transaction is rolled back"
            testService.lastEvent = null
            testService.insertAndRollback()

        then:
            def e = thrown(RuntimeException)
            e.message == 'Bad things happened'
            testService.lastEvent == null
            testService.readTransactionally() == 1
            testService.commitCount.get() + testService.rollbackCount.get() == testService.completionCount.get()

        when:"A transaction is rolled back"
            testService.insertAndRollbackChecked()

        then:
            def e2 = thrown(Exception)
            e2.message == 'Bad things happened'
            testService.lastEvent == null
            testService.readTransactionally() == 1
            testService.commitCount.get() + testService.rollbackCount.get() == testService.completionCount.get()

        when:"A transaction is rolled back but the exception ignored"
            testService.insertAndRollbackDontRollbackOn()

        then:
            thrown(IOException)
            testService.readTransactionally() == 2
            testService.lastEvent
            testService.commitCount.get() + testService.rollbackCount.get() == testService.completionCount.get()

        when:"Test that connections are never exhausted"
            int i = 0
            200.times { i += testService.readTransactionally() }

        then:"We're ok at it completed"
            i == 400
    }

    void "test transactional1"() {
        given:
            testService.init()

        when:
            testService.doWork1()

        then:
            asyncConditions.evaluate {
                testService.readTransactionally() == 4
                testService.commitCount.get() + testService.rollbackCount.get() == testService.completionCount.get()
            }
    }

    void "test transactional calling #workMethod"() {
        given:
            testService.init()

        when:
            testService."$workMethod"()

        then:
            asyncConditions.evaluate {
                testService.readTransactionally() == 4
                testService.commitCount.get() + testService.rollbackCount.get() == testService.completionCount.get()
            }

        where:
            workMethod << ["doWork1", "doWork2", "doWork3", "doWork4", "doWork5"]
    }

    @Singleton
    static class TestService {
        @Inject Connection connection
        @Inject ApplicationEventPublisher eventPublisher
        NewBookEvent lastEvent

        AtomicInteger commitCount
        AtomicInteger rollbackCount
        AtomicInteger completionCount

        @Transactional
        void init() {
            connection.prepareStatement("drop table book if exists").execute()
            connection.prepareStatement("create table book (id bigint not null auto_increment, pages integer not null, title varchar(255), primary key (id))").execute()

            connection.prepareStatement("drop table book if exists").execute()
            connection.prepareStatement("create table book (id bigint not null auto_increment, pages integer not null, title varchar(255), primary key (id))").execute()

            commitCount = new AtomicInteger(0)
            rollbackCount = new AtomicInteger(0)
            completionCount = new AtomicInteger(0)
        }

        @Transactional
        void insertWithTransaction() {
            addBook('The Shining')
            eventPublisher.publishEvent(new NewBookEvent("The Stand"))
        }

        @Transactional
        void insertAndRollback() {
            addBook('The Shining')
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new RuntimeException("Bad things happened")
        }


        @Transactional
        void insertAndRollbackChecked() {
            addBook('The Shining')
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new Exception("Bad things happened")
        }

        @Transactional(dontRollbackOn = IOException)
        void insertAndRollbackDontRollbackOn() {
            addBook('The Shining')
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

        @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_COMMIT)
        void afterCommit(NewBookEvent event) {
            lastEvent = event
            commitCount.incrementAndGet()
        }

        @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_ROLLBACK)
        void afterRollback(NewBookEvent event) {
            rollbackCount.incrementAndGet()
        }

        @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_COMPLETION)
        void afterCompletion(NewBookEvent event) {
            completionCount.incrementAndGet()
        }

        @Transactional
        void doWork1() {
            doReadOnly("ABC")
            doInnerWork("XYZ")
            notSupports()
            notSupportsAsync()
            doMandatoryInnerWork("YES")
            doInnerWorkAsync("FooBar")
        }

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        void doWork2() {
            doReadOnly("ABC")
            doInnerWork("XYZ")
            notSupports()
            notSupportsAsync()
            doMandatoryInnerWork("YES")
            doInnerWorkAsync("FooBar")
        }

        @Transactional
        @Async
        void doWork3() {
            doReadOnly("ABC")
            doInnerWork("XYZ")
            notSupports()
            notSupportsAsync()
            doMandatoryInnerWork("YES")
            doInnerWorkAsync("FooBar")
        }

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        @Async
        void doWork4() {
            doReadOnly("ABC")
            doInnerWork("XYZ")
            notSupports()
            notSupportsAsync()
            doMandatoryInnerWork("YES")
            doInnerWorkAsync("FooBar")
        }

        void doWork5() {
            doReadOnly("ABC")
            doInnerWork("XYZ")
            notSupports()
            notSupportsAsync()
            doInnerWorkAsync("FooBar")
        }

        @Transactional
        void doReadOnly(String book) {
            addBook(book)
        }

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        void doInnerWork(String book) {
            addBook(book)
        }

        @Transactional(Transactional.TxType.MANDATORY)
        void doMandatoryInnerWork(String book) {
            addBook(book)
        }

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        @Async
        void doInnerWorkAsync(String book) {
            addBook(book)
        }

        @Transactional(Transactional.TxType.NOT_SUPPORTED)
        void notSupports() {
        }

        @Transactional(Transactional.TxType.NOT_SUPPORTED)
        @Async
        void notSupportsAsync() {
        }

        private void addBook(String book) {
            def ps1 = connection.prepareStatement("insert into book (pages, title) values(200, '$book')")
            ps1.execute()
            ps1.close()
        }
    }

    static class NewBookEvent {
        final String title

        NewBookEvent(String title) {
            this.title = title
        }
    }
}

