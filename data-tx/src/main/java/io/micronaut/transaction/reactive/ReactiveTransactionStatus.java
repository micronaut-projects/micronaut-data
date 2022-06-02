/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.transaction.reactive;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.transaction.TransactionExecution;

/**
 * Status object for reactive transactions.
 *
 * @param <T> The connection type.
 * @author graemerocher
 * @since 2.2.0
 */
public interface ReactiveTransactionStatus<T> extends TransactionExecution {
    /**
     * Attribute that can be used to store this status within context propagation services.
     * @deprecated The key cannot be constant in multi-tx environments. Replaced with {@link ReactorReactiveTransactionOperations#getTransactionStatus(reactor.util.context.ContextView)}.
     */
    @Deprecated
    String ATTRIBUTE = "io.micronaut.tx.ATTRIBUTE";

    /**
     * Attribute that can be used to store the transaction status.
     * @deprecated The key cannot be constant in multi-tx environments. Replaced with {@link ReactorReactiveTransactionOperations#getTransactionDefinition(reactor.util.context.ContextView)}.
     */
    @Deprecated
    String STATUS = "io.micronaut.tx.STATUS";

    /**
     * @return The current connection.
     */
    @NonNull T getConnection();
}
