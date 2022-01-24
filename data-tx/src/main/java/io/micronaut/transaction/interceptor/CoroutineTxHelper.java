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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import jakarta.inject.Singleton;
import kotlin.coroutines.CoroutineContext;

/**
 * Helper to setup Kotlin coroutine context.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
@Singleton
@Requires(classes = kotlin.coroutines.CoroutineContext.class)
final class CoroutineTxHelper {

    public void setupCoroutineContext(KotlinInterceptedMethod kotlinInterceptedMethod) {
        CoroutineContext existingContext = kotlinInterceptedMethod.getCoroutineContext();
        kotlinInterceptedMethod.updateCoroutineContext(existingContext.plus(new TxSynchronousContext(TransactionSynchronizationManager.copyState())));
    }

}
