/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.transaction;

import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.support.AbstractConnectionOperations;

/**
 * The synchronous counterpart of {@link ReactiveConnManager}: records the connection operations in the {@link OpLogger}.
 */
final class SyncConnManager extends AbstractConnectionOperations<String> {

    private final OpLogger opLogger;
    private int connectionCount = 0;

    SyncConnManager(OpLogger opLogger) {
        this.opLogger = opLogger;
    }

    @Override
    protected String openConnection(ConnectionDefinition definition) {
        String connection = "CONNECTION_" + ++connectionCount;
        opLogger.add("OPEN " + connection);
        return connection;
    }

    @Override
    protected void setupConnection(ConnectionStatus<String> connectionStatus) {
        // The recorded connection has no state to set up
    }

    @Override
    protected void closeConnection(ConnectionStatus<String> connectionStatus) {
        opLogger.add("CLOSE " + connectionStatus.getConnection());
    }
}
