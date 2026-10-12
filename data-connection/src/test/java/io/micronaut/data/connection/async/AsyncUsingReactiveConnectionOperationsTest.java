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
package io.micronaut.data.connection.async;

import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.support.RecordingReactorConnectionOperations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncUsingReactiveConnectionOperationsTest {

    private final List<String> events = new ArrayList<>();
    private final AsyncUsingReactiveConnectionOperations<String> operations =
        new AsyncUsingReactiveConnectionOperations<>(new RecordingReactorConnectionOperations(events));

    @Test
    void connectionStatusIsFoundInsideAConnection() throws Exception {
        String result = operations.withConnection(status -> {
            Optional<ConnectionStatus<String>> found = operations.findConnectionStatus();
            assertSame(status, found.orElseThrow());
            assertSame(status, operations.getConnectionStatus());
            return CompletableFuture.completedFuture(status.getConnection());
        }).toCompletableFuture().get();

        assertEquals("CONNECTION_1", result);
        assertEquals(List.of("OPEN CONNECTION_1", "CLOSE CONNECTION_1"), events);
    }

    @Test
    void optionalLookupThrowsWithoutAConnection() {
        assertThrows(NoConnectionException.class, operations::findConnectionStatus);
    }

    @Test
    void requiredLookupThrowsWithoutAConnection() {
        assertThrows(NoConnectionException.class, operations::getConnectionStatus);
    }
}
