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
package io.micronaut.data.connection.support;

import io.micronaut.data.connection.ConnectionDefinition;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive connection operations that record when a connection is opened and closed.
 */
public final class RecordingReactorConnectionOperations extends AbstractReactorConnectionOperations<String> {

    private final List<String> events;
    private int connectionCount = 0;

    public RecordingReactorConnectionOperations(List<String> events) {
        this.events = events;
    }

    @Override
    protected Publisher<String> openConnection(ConnectionDefinition definition) {
        return Mono.fromSupplier(() -> {
            String connection = "CONNECTION_" + ++connectionCount;
            events.add("OPEN " + connection);
            return connection;
        });
    }

    @Override
    protected Publisher<Void> closeConnection(String connection, ConnectionDefinition definition) {
        return Mono.fromRunnable(() -> events.add("CLOSE " + connection));
    }
}
