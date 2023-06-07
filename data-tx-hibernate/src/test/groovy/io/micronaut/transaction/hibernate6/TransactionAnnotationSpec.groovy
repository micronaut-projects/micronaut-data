package io.micronaut.transaction.hibernate6

import io.micronaut.context.BeanDefinitionRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.data.tck.entities.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.annotation.TransactionalEventListener
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import javax.transaction.Transactional

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Stepwise
class TransactionAnnotationSpec extends Specification implements H2Properties {

    @Inject TestService testService
    @Inject BeanDefinitionRegistry registry

    void "test transactional annotation handling"() {

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


        when:"Test that connections are never exhausted"
        int i = 0
        300.times { i += testService.readTransactionally() }

        then:"We're ok at it completed"
        i == 300
    }

    @Singleton
    static class TestService {
        @Inject EntityManager entityManager
        @Inject ApplicationEventPublisher eventPublisher
        NewBookEvent lastEvent

        @Transactional
        void insertWithTransaction() {
            entityManager.persist(new Book(title:"The Stand", totalPages: 1000))
            eventPublisher.publishEvent(new NewBookEvent("The Stand"))
        }

        @Transactional
        void insertAndRollback() {
            entityManager.persist(new Book(title:"The Shining", totalPages: 500))
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new RuntimeException("Bad things happened")
        }


        @Transactional
        void insertAndRollbackChecked() {
            entityManager.persist(new Book(title:"The Shining", totalPages: 500))
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new Exception("Bad things happened")
        }


        @Transactional
        int readTransactionally() {
            return entityManager.createQuery("select count(book) as count from Book book", Long.class)
                    .singleResult.intValue()
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
