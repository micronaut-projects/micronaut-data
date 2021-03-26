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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.UpdateAllEntriesAsyncInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link UpdateAllEntriesAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author Denis Stepanov
 * @since 2.4.0
 */
public class DefaultUpdateAllAsyncEntitiesInterceptor<T> extends AbstractAsyncInterceptor<T, Object>
        implements UpdateAllEntriesAsyncInterceptor<T, CompletionStage<Object>> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    public DefaultUpdateAllAsyncEntitiesInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public CompletionStage<Object> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<Object>> context) {
        Iterable<T> iterable = (Iterable<T>) getEntitiesParameter(context, Object.class);
        //noinspection unchecked
        Class<T> rootEntity = (Class<T>) getRequiredRootEntity(context);
        CompletionStage<Iterable<T>> future = asyncDatastoreOperations.updateAll(getUpdateAllBatchOperation(context, rootEntity, iterable));
        ReturnType<CompletionStage<Object>> rt = context.getReturnType();
        Argument<CompletionStage<Object>> rtArgument = context.getReturnType().asArgument();
        Argument<Object> csValueArgument = (Argument<Object>) rtArgument.getFirstTypeVariable().orElse(Argument.listOf(Object.class));
        if (isNumber(csValueArgument.getType())) {
            return future.thenApply(it -> ConversionService.SHARED.convertRequired(count(it), csValueArgument));
        }
        return future.thenApply(it -> ConversionService.SHARED.convertRequired(
                it,
                csValueArgument
        ));
    }
}
