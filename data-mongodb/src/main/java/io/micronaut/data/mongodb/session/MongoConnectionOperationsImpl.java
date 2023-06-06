/*
 * Copyright 2017-2023 original authors
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

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
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
