package io.micronaut.data.document.mongodb;

import com.mongodb.BasicDBObject;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.BsonDocument;

@Singleton
public class MongoTestService {
    @Inject
    SynchronousTransactionManager<ClientSession> transactionManager;
    @Inject
    MongoClient mongoClient;

    void insertWithTransaction() {
        transactionManager.executeWrite(status -> {
            MongoCollection<BsonDocument> collection = getDatabase().getCollection("things", BsonDocument.class);
            collection.insertOne(new BasicDBObject().append("name", "Xyz").toBsonDocument());
            return null;
        });
    }

    void insertAndRollback() {
        transactionManager.execute(TransactionDefinition.DEFAULT, status -> {
            MongoCollection<BsonDocument> collection = getDatabase().getCollection("things", BsonDocument.class);
            collection.insertOne(new BasicDBObject().append("name", "FAIL").toBsonDocument());
            throw new RuntimeException("Bad things happened");
        });
    }


    void insertAndRollbackChecked() {
        transactionManager.execute(TransactionDefinition.DEFAULT, status -> {
            MongoCollection<BsonDocument> collection = getDatabase().getCollection("things", BsonDocument.class);
            collection.insertOne(new BasicDBObject().append("name", "FAIL").toBsonDocument());
            throw new RuntimeException("Bad things happened");
        });
    }

    int readTransactionally() {
        return transactionManager.executeRead(status -> {
            MongoCollection<BsonDocument> collection = getDatabase().getCollection("things", BsonDocument.class);
            return (int) collection.countDocuments();
        });
    }

    @NonNull
    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase("default");
    }
}
