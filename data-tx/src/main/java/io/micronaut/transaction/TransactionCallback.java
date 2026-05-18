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
package io.micronaut.transaction;

import org.jspecify.annotations.Nullable;
import io.micronaut.transaction.support.ExceptionUtil;

import java.util.function.Function;

/**
 * A functional interface for running code that runs within the context of a transaction.
 *
 * @author graemerocher
 * @param <T> The connection type
 * @param <R> The return type
 */
@FunctionalInterface
public interface TransactionCallback<T, R extends @Nullable Object> extends Function<TransactionStatus<T>, R> {

    @Override
    @SuppressWarnings("FunctionalInterfaceMethodChanged")
    default R apply(TransactionStatus<T> status) {
        try {
            return call(status);
        } catch (Exception e) {
            return ExceptionUtil.sneakyThrow(e);
        }
    }

    /**
     * Code that runs within the context of a transaction will implement this method.
     *
     * @param status The transaction status.
     *
     * @return The return value
     * @throws Exception When an error occurs in invoking the transaction
     */
    R call(TransactionStatus<T> status) throws Exception;
}
