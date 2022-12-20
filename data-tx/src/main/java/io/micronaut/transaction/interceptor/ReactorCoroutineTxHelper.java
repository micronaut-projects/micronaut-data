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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.reactor.ReactorContext;
import reactor.util.context.ContextView;

/**
 * Helper to setup Kotlin coroutine context with Reactor.
 *
 * @author Denis Stepanov
 * @since 3.9.1
 */
@Internal
@Singleton
@Requires(classes = {CoroutineContext.class, ReactorContext.class})
public final class ReactorCoroutineTxHelper {

    @Nullable
    public ContextView getReactorContext(@NonNull KotlinInterceptedMethod kotlinInterceptedMethod) {
        CoroutineContext coroutineContext = kotlinInterceptedMethod.getCoroutineContext();
        CoroutineContext.Key key = ReactorContext.Key;
        Object element = coroutineContext.get(key);
        ReactorContext reactorContext = (ReactorContext) element;
        if (reactorContext != null) {
            kotlinInterceptedMethod.updateCoroutineContext(coroutineContext);
            return reactorContext.getContext();
        }
        return null;
    }

}
