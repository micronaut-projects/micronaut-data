/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.mongodb.session;

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.support.AbstractReactorReactiveConnectionOperations;
import io.micronaut.data.mongodb.conf.RequiresReactiveMongo;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * The reactive MongoDB connection operations implementation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@RequiresReactiveMongo
@EachBean(MongoClient.class)
@Internal
final class DefaultReactiveMongoSessionOperations extends AbstractReactorReactiveConnectionOperations<ClientSession>
    implements MongoReactorReactiveConnectionOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveMongoSessionOperations.class);

    private final String serverName;
    private final MongoClient mongoClient;

    DefaultReactiveMongoSessionOperations(@Parameter String serverName, MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        this.serverName = serverName;
    }

    @Override
    @NonNull
    protected Publisher<ClientSession> openConnection(@NonNull ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Opening Connection for MongoDB configuration: {} and definition: {}", serverName, definition);
        }
        return mongoClient.startSession();
    }

    @Override
    @NonNull
    protected Publisher<Void> closeConnection(@NonNull ClientSession connection, @NonNull ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing Connection for MongoDB configuration: {} and definition: {}", serverName, definition);
        }
        connection.close();
        return Mono.empty();
    }

}
