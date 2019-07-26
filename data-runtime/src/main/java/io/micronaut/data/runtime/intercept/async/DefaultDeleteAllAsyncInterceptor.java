/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.concurrent.CompletionStage;


/**
 * Default implementation of {@link DeleteAllAsyncInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteAllAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Number> implements DeleteAllAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultDeleteAllAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Number> intercept(MethodInvocationContext<T, CompletionStage<Number>> context) {
        Argument<CompletionStage<Number>> arg = context.getReturnType().asArgument();
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(context);
            return asyncDatastoreOperations.executeUpdate(preparedQuery)
                        .thenApply(number -> convertNumberArgumentIfNecessary(number, arg));
        } else {
            Object[] parameterValues = context.getParameterValues();
            Class<Object> rootEntity = (Class<Object>) getRequiredRootEntity(context);
            if (parameterValues.length == 1 && parameterValues[0] instanceof Iterable) {
                BatchOperation<Object> batchOperation = getBatchOperation(context, rootEntity, (Iterable<Object>) parameterValues[0]);
                return asyncDatastoreOperations.deleteAll(batchOperation)
                        .thenApply(number -> convertNumberArgumentIfNecessary(number, arg));
            } else if (parameterValues.length == 0) {
                BatchOperation<Object> batchOperation = getBatchOperation(context, rootEntity);
                return asyncDatastoreOperations.deleteAll(batchOperation)
                        .thenApply(number -> convertNumberArgumentIfNecessary(number, arg));
            } else {
                throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
            }
        }
    }

}
