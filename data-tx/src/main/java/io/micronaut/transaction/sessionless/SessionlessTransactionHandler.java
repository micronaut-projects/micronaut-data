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
package io.micronaut.transaction.sessionless;

import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.support.TransactionResourceCommit;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Starts or resumes a vendor-specific sessionless transaction on a transaction manager's prepared resource.
 *
 * @since 5.2
 */
@Internal
public interface SessionlessTransactionHandler {

    /**
     * @param definition The transaction definition
     * @return Whether this handler supports the transaction definition
     */
    boolean supports(@NonNull TransactionDefinition definition);

    /**
     * Begins sessionless handling before application code executes.
     *
     * @param status The newly started transaction
     * @param definition The transaction definition
     * @return Work to perform at the resource commit boundary, or {@code null} for a normal resource commit
     */
    @Nullable
    TransactionResourceCommit begin(@NonNull TransactionStatus<?> status,
                                    @NonNull TransactionDefinition definition);
}
