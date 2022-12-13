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

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.kotlin.KotlinInterceptedMethod;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * The transactional state propagation in {@link CompletionStage} helper.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Internal
@Singleton
public final class TxCompletionStageDataIntroductionHelper {

    private final CoroutineTxHelper coroutineTxHelper;

    public TxCompletionStageDataIntroductionHelper(@Nullable CoroutineTxHelper coroutineTxHelper) {
        this.coroutineTxHelper = coroutineTxHelper;
    }

    /**
     * Decorate the supplied {@link CompletionStage}.
     *
     * @param interceptedMethod The intercepted method
     * @param supplier          The supplier
     * @return decorated stage
     */
    public CompletionStage<Object> decorate(InterceptedMethod interceptedMethod, Supplier<CompletionStage<Object>> supplier) {
        TransactionSynchronizationManager.TransactionSynchronizationState state = null;
        if (interceptedMethod instanceof KotlinInterceptedMethod) {
            KotlinInterceptedMethod kotlinInterceptedMethod = (KotlinInterceptedMethod) interceptedMethod;
            state = Objects.requireNonNull(coroutineTxHelper).getTxState(kotlinInterceptedMethod);
        }
        return TransactionSynchronizationManager.decorateCompletionStage(state, supplier);
    }
}
