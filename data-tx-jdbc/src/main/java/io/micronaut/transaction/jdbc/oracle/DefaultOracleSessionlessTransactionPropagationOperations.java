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
package io.micronaut.transaction.jdbc.oracle;

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Default implementation of {@link OracleSessionlessTransactionPropagationOperations}.
 *
 * <p>This implementation creates a lexical {@link PropagatedContext} scope that contains a single
 * {@link OracleSessionlessTransactionState}. The transaction manager uses that state to publish the
 * GTRID produced by {@link OracleTransactional.Sessionless#SUSPEND} and to
 * consume the GTRID required by {@link OracleTransactional.Sessionless#REQUIRES_SUSPENDED}.
 * Encoded transaction identifiers are converted through {@link OracleSessionlessTransactionIdCodec}, so
 * applications can replace the codec without changing propagation mechanics.</p>
 */
@Singleton
final class DefaultOracleSessionlessTransactionPropagationOperations implements OracleSessionlessTransactionPropagationOperations {

    private final OracleSessionlessTransactionIdCodec transactionIdCodec;

    /**
     * Creates the default propagation operations.
     *
     * @param transactionIdCodec The codec used to encode and decode transaction identifiers
     */
    DefaultOracleSessionlessTransactionPropagationOperations(OracleSessionlessTransactionIdCodec transactionIdCodec) {
        this.transactionIdCodec = transactionIdCodec;
    }

    @Override
    public <T extends @Nullable Object> T withPropagation(Supplier<T> supplier) {
        OracleSessionlessTransactionState state = new OracleSessionlessTransactionState();
        return withPropagation(state, supplier);
    }

    @Override
    public <T extends @Nullable Object> T withPropagation(String encodedTransactionId, Supplier<T> supplier) {
        OracleSessionlessTransactionState state = new OracleSessionlessTransactionState();
        state.setGtrid(transactionIdCodec.decode(encodedTransactionId));
        return withPropagation(state, supplier);
    }

    @Override
    public Optional<String> currentTransactionId() {
        return OracleSessionlessTransactionState.current()
            .flatMap(state -> state.getGtrid().map(transactionIdCodec::encode));
    }

    @Override
    public void setTransactionId(String encodedTransactionId) {
        OracleSessionlessTransactionState.current().orElseThrow(() ->
            new TransactionUsageException("Oracle sessionless transaction propagation is not active")
        ).setGtrid(transactionIdCodec.decode(encodedTransactionId));
    }

    @Override
    public void clearTransactionId() {
        OracleSessionlessTransactionState.current().ifPresent(OracleSessionlessTransactionState::clearGtrid);
    }

    /**
     * Executes the supplier with the provided sessionless transaction state installed in the current
     * propagation context.
     *
     * <p>Programmatic propagation is lexical: the new state is visible only while the supplier runs,
     * and any previous state is restored by {@link PropagatedContext} when the supplier exits.</p>
     *
     * @param state The state to propagate
     * @param supplier The supplier to execute
     * @param <T> The result type
     * @return The supplier result
     */
    private static <T extends @Nullable Object> T withPropagation(OracleSessionlessTransactionState state,
                                                                  Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        PropagatedContext context = OracleSessionlessTransactionState
            .withoutExisting(PropagatedContext.getOrEmpty())
            .plus(state);
        return context.propagate(supplier);
    }
}
