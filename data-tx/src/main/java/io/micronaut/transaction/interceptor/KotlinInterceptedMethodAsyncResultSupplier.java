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
package io.micronaut.transaction.interceptor;

import io.micronaut.aop.kotlin.KotlinInterceptedMethod;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.async.AsyncTransactionStatus;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The supplier of the Kotlin intercepted method result, allows to access the actual intercepted method.
 *
 * @param <C> The connection type
 * @param <T> The supplied type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class KotlinInterceptedMethodAsyncResultSupplier<C, T> implements Function<AsyncTransactionStatus<C>, CompletionStage<T>> {

    private final KotlinInterceptedMethod kotlinInterceptedMethod;

    KotlinInterceptedMethodAsyncResultSupplier(KotlinInterceptedMethod kotlinInterceptedMethod) {
        this.kotlinInterceptedMethod = kotlinInterceptedMethod;
    }

    @Override
    public CompletionStage<T> apply(AsyncTransactionStatus<C> cAsyncTransactionStatus) {
        return (CompletionStage<T>) kotlinInterceptedMethod.interceptResultAsCompletionStage();
    }

    public KotlinInterceptedMethod getKotlinInterceptedMethod() {
        return kotlinInterceptedMethod;
    }
}
