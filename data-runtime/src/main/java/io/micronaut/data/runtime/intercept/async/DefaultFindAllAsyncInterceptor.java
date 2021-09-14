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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.FindAllAsyncInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Collections;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The default implementation of {@link FindAllAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindAllAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Iterable<Object>> implements FindAllAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultFindAllAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Iterable<Object>> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<Iterable<Object>>> context) {
        CompletionStage<? extends Iterable<?>> future;
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);
            future = asyncDatastoreOperations.findAll(preparedQuery);

        } else {
            future = asyncDatastoreOperations.findAll(getPagedQuery(context));
        }
        return future.thenApply((Function<Iterable<?>, Iterable<Object>>) iterable -> {
            Argument<?> argument = findReturnType(context).orElse(Argument.listOf(Object.class));
            Iterable<Object> result = (Iterable<Object>) operations.getConversionService().convert(
                    iterable,
                    argument
            ).orElse(null);
            return result == null ? Collections.emptyList() : result;
        });
    }
}
