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
package io.micronaut.data.r2dbc.connection;

import io.micronaut.core.annotation.Internal;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.IsolationLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

/**
 * The state of a connection when it was opened, restored before the connection is closed (returned to the pool).
 * Some drivers (e.g. Oracle R2DBC) keep auto-commit disabled after the transaction ends: a pooled connection
 * would run the next statements in an implicit transaction that is never committed.
 * The isolation level set by a transaction would also leak to the next user of the connection.
 * <p>
 * The initial state is the state of the connection handed out by the connection factory (pool).
 * If it was already changed outside of Micronaut Data (e.g. by a raw connection usage), the changed state is preserved.
 *
 * @param autoCommit     The auto-commit
 * @param isolationLevel The isolation level
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public record R2dbcConnectionState(boolean autoCommit, @Nullable IsolationLevel isolationLevel) {

    /**
     * Capture the current state of the connection.
     *
     * @param connection The connection
     * @return The state
     */
    public static R2dbcConnectionState capture(Connection connection) {
        return new R2dbcConnectionState(connection.isAutoCommit(), connection.getTransactionIsolationLevel());
    }

    /**
     * Restore the state of the connection. An error is logged and not propagated, so the connection can be closed.
     *
     * @param connection The connection
     * @param log        The logger
     * @return The publisher
     */
    public Mono<Void> restore(Connection connection, Logger log) {
        return restore(connection)
            .onErrorResume(e -> {
                log.warn("Failed to restore the state of the R2DBC connection", e);
                return Mono.empty();
            });
    }

    private Mono<Void> restore(Connection connection) {
        return Mono.defer(() -> {
            Mono<Void> result = Mono.empty();
            if (connection.isAutoCommit() != autoCommit) {
                if (autoCommit) {
                    // Enabling auto-commit commits an active transaction, discard the work that wasn't committed
                    result = result.then(Mono.from(connection.rollbackTransaction()));
                }
                result = result.then(Mono.from(connection.setAutoCommit(autoCommit)));
            }
            if (isolationLevel != null && !isolationLevel.equals(connection.getTransactionIsolationLevel())) {
                result = result.then(Mono.from(connection.setTransactionIsolationLevel(isolationLevel)));
            }
            return result;
        });
    }
}
