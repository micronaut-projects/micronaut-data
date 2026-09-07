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

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Operations for non-HTTP propagation of Oracle sessionless transaction identifiers.
 *
 * <p>The HTTP server filter installs the same propagation state for HTTP requests. Code running outside
 * HTTP can use this API to create an equivalent propagation scope and exchange encoded transaction
 * identifiers with other transports.</p>
 *
 * @since 5.2.0
 */
@Experimental
public interface OracleSessionlessTransactionPropagationOperations {

    /**
     * Executes the supplier with an empty Oracle sessionless transaction propagation state.
     *
     * @param supplier The supplier to execute
     * @param <T> The result type
     * @return The supplier result
     */
    <T extends @Nullable Object> T withPropagation(Supplier<T> supplier);

    /**
     * Executes the supplier with an Oracle sessionless transaction identifier already available to resume.
     *
     * @param encodedTransactionId The transaction identifier encoded by {@link OracleSessionlessTransactionIdCodec}
     * @param supplier The supplier to execute
     * @param <T> The result type
     * @return The supplier result
     */
    <T extends @Nullable Object> T withPropagation(String encodedTransactionId, Supplier<T> supplier);

    /**
     * @return The current transaction identifier encoded by {@link OracleSessionlessTransactionIdCodec}, if one is available
     */
    Optional<String> currentTransactionId();

    /**
     * Replaces the current transaction identifier in the active propagation state.
     *
     * @param encodedTransactionId The transaction identifier encoded by {@link OracleSessionlessTransactionIdCodec}
     */
    void setTransactionId(String encodedTransactionId);

    /**
     * Clears the current transaction identifier from the active propagation state.
     */
    void clearTransactionId();
}
