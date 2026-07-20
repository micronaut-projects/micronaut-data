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
package io.micronaut.transaction.recovery;

import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a transaction recovery outcome for a transaction using a vendor-specific mechanism.
 *
 * @since 5.2
 */
@Internal
public interface CommitOutcomeResolver {

    /**
     * Capture a vendor-specific token from the active transaction before commit.
     *
     * @param status The transaction status
     * @return A vendor-specific token, or {@code null} if capture is not supported
     */
    @Nullable
    Object captureLtxid(@NonNull TransactionStatus<?> status);

    /**
     * Resolve the outcome for the previously captured token.
     *
     * @param token The token captured by {@link #captureLtxid(TransactionStatus)}
     * @return The recovery outcome
     */
    @NonNull
    CommitOutcome resolve(@NonNull Object token);
}
