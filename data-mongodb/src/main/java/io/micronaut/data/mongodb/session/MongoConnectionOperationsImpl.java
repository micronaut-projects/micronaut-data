package io.micronaut.data.mongodb.session;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.support.AbstractConnectionOperations;

@Internal
@EachBean(MongoClient.class)
final class MongoConnectionOperationsImpl extends AbstractConnectionOperations<ClientSession> implements MongoConnectionOperations {

    private final MongoClient mongoClient;

    MongoConnectionOperationsImpl(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @Override
    protected ClientSession openConnection(ConnectionDefinition definition) {
        return mongoClient.startSession();
    }

    @Override
    protected void setupConnection(ConnectionStatus<ClientSession> connectionStatus) {
//        if (connectionStatus.getDefinition().isP) {
//            throw new ConnectionException("MongoDB client doesn't support read only client session!");
//        }
    }

    @Override
    protected void closeConnection(ConnectionStatus<ClientSession> connectionStatus) {
        connectionStatus.getConnection().close();
    }
}
