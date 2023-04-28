package io.micronaut.data.mongodb.session;

import com.mongodb.client.ClientSession;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;

public interface MongoConnectionOperations extends ConnectionOperations<ClientSession> {
}
