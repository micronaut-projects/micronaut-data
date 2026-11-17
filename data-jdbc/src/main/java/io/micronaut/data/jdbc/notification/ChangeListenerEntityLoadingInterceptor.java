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
package io.micronaut.data.jdbc.notification;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.ChangeListener;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Materializes provider-deferred entity state before change-listener execution.
 *
 * <p>The interceptor runs immediately inside retry advice and before transaction and async advice.
 * Loading can therefore be retried without invoking or scheduling the listener. The listener's
 * transaction advice does not wrap the preceding load, and async advice only offloads listener execution.
 * A successfully materialized event is reused if synchronous listener execution is retried.</p>
 */
@Internal
@Singleton
@InterceptorBean(ChangeListener.class)
public final class ChangeListenerEntityLoadingInterceptor implements MethodInterceptor<Object, Object> {

    @Override
    public int getOrder() {
        return InterceptPhase.RETRY.getPosition() + 1;
    }

    /**
     * Materializes a deferred notification entity before proceeding to transaction and async
     * advice and the listener.
     *
     * @param context the listener invocation context
     * @return the listener invocation result
     */
    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 1 && parameterValues[0] instanceof DeferredChangeEvent<?> event) {
            event.materialize();
        }
        return context.proceed();
    }
}
