package io.micronaut.data.document.mongodb

import com.mongodb.BasicDBObject
import com.mongodb.client.ClientSession
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.bson.BsonDocument
import spock.lang.Specification
import spock.lang.Stepwise

import javax.transaction.Transactional

@MicronautTest(transactional = false)
@Stepwise
class MongoTransactionAnnotationSpec extends Specification implements MongoTestPropertyProvider {

    @Inject
    TestService testService

    void "test transactional annotation handling"() {
        when: "an insert is performed in a transaction"
            testService.insertWithTransaction()

        then: "The insert worked"
            testService.lastEvent?.title == "The Stand"
            testService.readTransactionally() == 1

        when: "A transaction is rolled back"
            testService.lastEvent = null
            testService.insertAndRollback()

        then:
            def e = thrown(RuntimeException)
            e.message == 'Bad things happened'
            testService.lastEvent == null
            testService.readTransactionally() == 1


        when: "A transaction is rolled back"
            testService.insertAndRollbackChecked()

        then:
            def e2 = thrown(Exception)
            e2.message == 'Bad things happened'
            testService.lastEvent == null
            testService.readTransactionally() == 1

        when: "A transaction is rolled back but the exception ignored"
            testService.insertAndRollbackDontRollbackOn()

        then:
            thrown(IOException)
            testService.readTransactionally() == 2
            testService.lastEvent


        when: "Test that connections are never exhausted"
            int i = 0
            200.times { i += testService.readTransactionally() }

        then: "We're ok at it completed"
            i == 400
    }

    @Singleton
    static class TestService {
        @Inject
        ApplicationEventPublisher eventPublisher

        NewBookEvent lastEvent
        @Inject
        MongoClient mongoClient
        @Inject
        SynchronousTransactionManager<ClientSession> manager

        @Transactional
        void insertWithTransaction() {
            MongoCollection<BsonDocument> collection = getBooks()
            collection.insertOne(manager.getConnection(), new BasicDBObject().append("name", "The Stand").toBsonDocument());
            eventPublisher.publishEvent(new NewBookEvent("The Stand"))
        }

        @Transactional
        void insertAndRollback() {
            MongoCollection<BsonDocument> collection = getBooks()
            collection.insertOne(manager.getConnection(), new BasicDBObject().append("name", "The Shining").toBsonDocument());
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new RuntimeException("Bad things happened")
        }

        @Transactional
        void insertAndRollbackChecked() {
            MongoCollection<BsonDocument> collection = getBooks()
            collection.insertOne(manager.getConnection(), new BasicDBObject().append("name", "The Shining").toBsonDocument());
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new Exception("Bad things happened")
        }

        @Transactional(dontRollbackOn = IOException)
        void insertAndRollbackDontRollbackOn() {
            MongoCollection<BsonDocument> collection = getBooks()
            collection.insertOne(manager.getConnection(), new BasicDBObject().append("name", "The Shining").toBsonDocument());
            eventPublisher.publishEvent(new NewBookEvent("The Shining"))
            throw new IOException("Bad things happened")
        }

        @Transactional
        int readTransactionally() {
            MongoCollection<BsonDocument> collection = getBooks()
            return collection.countDocuments(manager.getConnection())
        }

        @TransactionalEventListener
        void afterCommit(NewBookEvent event) {
            lastEvent = event
        }

        private MongoDatabase getDatabase() {
            return mongoClient.getDatabase("default");
        }

        private MongoCollection<BsonDocument> getBooks() {
            MongoCollection<BsonDocument> collection = getDatabase().getCollection("bookz", BsonDocument.class)
            collection
        }

    }

    static class NewBookEvent {
        final String title

        NewBookEvent(String title) {
            this.title = title
        }
    }
}
