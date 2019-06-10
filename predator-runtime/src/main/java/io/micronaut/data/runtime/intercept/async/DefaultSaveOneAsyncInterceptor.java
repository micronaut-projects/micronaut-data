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
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.intercept.async.SaveOneAsyncInterceptor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Default implementation of {@link SaveOneAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveOneAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Object> implements SaveOneAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultSaveOneAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Object> intercept(MethodInvocationContext<T, CompletionStage<Object>> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        Executor executor = asyncDatastoreOperations.getExecutor();
        return CompletableFuture.supplyAsync(() -> {
            Object o = instantiateEntity(rootEntity, parameterValueMap);
            return getInsertOperation(context, o);
        }, executor).thenCompose(asyncDatastoreOperations::persist);
    }
}
