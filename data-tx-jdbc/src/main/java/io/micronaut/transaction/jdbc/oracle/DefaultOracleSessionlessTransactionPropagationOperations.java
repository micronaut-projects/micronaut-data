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
import io.micronaut.transaction.exceptions.TransactionUsageException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
final class DefaultOracleSessionlessTransactionPropagationOperations implements OracleSessionlessTransactionPropagationOperations {

    private final OracleSessionlessTransactionIdCodec transactionIdCodec;

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

    private static <T extends @Nullable Object> T withPropagation(OracleSessionlessTransactionState state,
                                                                  Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        PropagatedContext context = OracleSessionlessTransactionState
            .withoutExisting(PropagatedContext.getOrEmpty())
            .plus(state);
        return context.propagate(supplier);
    }
}
