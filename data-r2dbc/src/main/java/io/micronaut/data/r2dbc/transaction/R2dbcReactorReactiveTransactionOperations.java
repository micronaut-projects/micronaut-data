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
package io.micronaut.data.r2dbc.transaction;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.r2dbc.spi.Connection;
import org.reactivestreams.Publisher;


/**
 * R2DBC transaction operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface R2dbcReactorReactiveTransactionOperations extends ReactorReactiveTransactionOperations<Connection> {

    /**
     * Execute the given handler with an existing transaction status.
     * @param status An existing in progress transaction status
     * @param handler The handler
     * @param <T> The emitted type
     * @return A publisher that emits the result type
     */
    @NonNull
    <T> Publisher<T> withTransaction(@NonNull ReactiveTransactionStatus<Connection> status, @NonNull TransactionalCallback<Connection, T> handler);


}
