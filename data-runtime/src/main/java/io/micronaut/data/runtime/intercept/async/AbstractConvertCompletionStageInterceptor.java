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
package io.micronaut.data.runtime.intercept.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;

/**
 * Abstract asynchronous interceptor implementation with a result conversion.
 *
 * @param <T> the value type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public abstract class AbstractConvertCompletionStageInterceptor<T> extends AbstractAsyncInterceptor<Object, T> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected AbstractConvertCompletionStageInterceptor(RepositoryOperations datastore) {
        super(datastore);
    }

    /**
     * Intercept CompletionStage.
     *
     * @param methodKey The method key
     * @param context   The context
     * @return the result publisher
     */
    protected abstract CompletionStage<?> interceptCompletionStage(RepositoryMethodKey methodKey, MethodInvocationContext<Object, CompletionStage<T>> context);

    @Override
    public CompletionStage<T> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, CompletionStage<T>> context) {
        CompletionStage<?> completionStage = interceptCompletionStage(methodKey, context);
        Argument<?> csValueArgument = getReturnType(context);
        if (csValueArgument.isVoid() || csValueArgument.getType() == Void.class || csValueArgument.isVoid()) {
            return completionStage.thenApply(o -> null);
        }
        return (CompletionStage<T>) completionStage.thenApply(o -> convertOne(context, o));
    }
}
