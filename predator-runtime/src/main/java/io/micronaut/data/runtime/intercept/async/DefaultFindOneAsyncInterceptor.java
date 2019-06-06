/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.intercept.async.FindOneAsyncInterceptor;
import io.micronaut.data.model.PreparedQuery;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of the {@link FindOneAsyncInterceptor} interface.
 *
 * @param <T> The declaring type.
 */
public class DefaultFindOneAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Object> implements FindOneAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected DefaultFindOneAsyncInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Object> intercept(MethodInvocationContext<T, CompletionStage<Object>> context) {
        PreparedQuery<Object, Object> preparedQuery = (PreparedQuery<Object, Object>) prepareQuery(context);
        CompletionStage<Object> future = asyncDatastoreOperations.findOne(preparedQuery);
        Argument<?> type = context.getReturnType().asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        return future.thenApply(o -> {
            if (!type.getType().isInstance(o)) {
                return ConversionService.SHARED.convert(o, type)
                        .orElseThrow(() -> new IllegalStateException("Unexpected return type: " + o));
            }
            return o;
        });
    }
}

