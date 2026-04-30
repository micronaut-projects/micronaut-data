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
package io.micronaut.transaction.support;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.TransactionDefinition;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Listener for reactive transaction execution lifecycle events.
 *
 * @param <C> The connection type
 * @since 5.0
 */
@Experimental
public interface ReactiveTransactionExecutionListener<C> extends Ordered {

    /**
     * Invoked before the transaction manager begins a transaction.
     *
     * @param connectionStatus The connection status
     * @param definition The transaction definition
     * @return The publisher
     */
    default Publisher<Void> beforeBegin(ConnectionStatus<C> connectionStatus, TransactionDefinition definition) {
        return Mono.empty();
    }

    /**
     * Invoked after the transaction manager begins a transaction.
     *
     * @param connectionStatus The connection status
     * @param definition The transaction definition
     * @return The publisher
     */
    default Publisher<Void> afterBegin(ConnectionStatus<C> connectionStatus, TransactionDefinition definition) {
        return Mono.empty();
    }
}
