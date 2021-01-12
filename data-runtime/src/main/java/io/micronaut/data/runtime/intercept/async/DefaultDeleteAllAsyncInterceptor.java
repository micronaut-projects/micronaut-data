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
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
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
    public CompletionStage<Number> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, CompletionStage<Number>> context) {
        Argument<CompletionStage<Number>> arg = context.getReturnType().asArgument();
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 1) {
            if (parameterValues[0] instanceof Iterable) {
                DeleteBatchOperation<Object> batchOperation = getDeleteBatchOperation(context, (Iterable<Object>) parameterValues[0]);
                return asyncDatastoreOperations.deleteAll(batchOperation)
                        .thenApply(number -> convertNumberArgumentIfNecessary(number, arg));
            } else {
                if (context.hasAnnotation(Query.class)) {
                    PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(methodKey, context);
                    // TODO: there should be executeDelete
                    return asyncDatastoreOperations.executeUpdate(preparedQuery)
                            .thenApply(number -> convertNumberArgumentIfNecessary(number, arg));
                }
                return asyncDatastoreOperations.delete(getDeleteOperation(context, parameterValues[0]))
                        .thenApply(number -> convertNumberArgumentIfNecessary(number, arg));
            }
        } else if (parameterValues.length == 0) {
            DeleteBatchOperation<Object> batchOperation = getDeleteAllBatchOperation(context);
            return asyncDatastoreOperations.deleteAll(batchOperation)
                    .thenApply(number -> convertNumberArgumentIfNecessary(number, arg));
        }
        throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
    }

}
