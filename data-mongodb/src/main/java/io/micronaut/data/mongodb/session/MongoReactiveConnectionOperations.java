package io.micronaut.data.mongodb.session;

import com.mongodb.reactivestreams.client.ClientSession;
import io.micronaut.data.connection.manager.reactive.ReactiveConnectionOperations;

public interface MongoReactiveConnectionOperations extends ReactiveConnectionOperations<ClientSession> {
}
