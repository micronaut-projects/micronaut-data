/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.transaction;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.async.AsyncTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;

/**
 * The registry of various transactional operations managers.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Internal
public interface TransactionOperationsRegistry {

    /**
     * Provide synchronous transaction operations.
     *
     * @param transactionManagerType The operations type
     * @param dataSourceName         The datasource name
     * @param <T>                    The operations type
     * @return the provided instance
     */
    @NonNull
    <T extends TransactionOperations<?>> T provideSynchronous(Class<T> transactionManagerType, @Nullable String dataSourceName);

    /**
     * Provide reactive transaction operations.
     *
     * @param transactionManagerType The operations type
     * @param dataSourceName         The datasource name
     * @param <T>                    The operations type
     * @return the provided instance
     */
    @NonNull
    <T extends ReactiveTransactionOperations<?>> T provideReactive(Class<T> transactionManagerType, @Nullable String dataSourceName);

    /**
     * Provide async transaction operations.
     *
     * @param transactionManagerType The operations type
     * @param dataSourceName         The datasource name
     * @param <T>                    The operations type
     * @return the provided instance
     */
    <T extends AsyncTransactionOperations<?>> T provideAsync(Class<T> transactionManagerType, @Nullable String dataSourceName);

}
