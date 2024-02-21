/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.intercept.DeleteAllReturningInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.DeleteReturningRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Optional;

/**
 * Default implementation of {@link DeleteAllInterceptor}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 4.2.0
 */
public class DefaultDeleteAllReturningInterceptor<E, R> extends AbstractQueryInterceptor<E, R> implements DeleteAllReturningInterceptor<E, R> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultDeleteAllReturningInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public R intercept(RepositoryMethodKey methodKey, MethodInvocationContext<E, R> context) {
        Argument<R> resultType = context.getReturnType().asArgument();
        Optional<Iterable<Object>> deleteEntities = findEntitiesParameter(context, Object.class);
        Optional<Object> deleteEntity = findEntityParameter(context, Object.class);
        if (deleteEntity.isEmpty() && deleteEntities.isEmpty()) {
            throw new IllegalStateException("Expected to find and entity");
        }
        if (deleteEntity.isPresent()) {
            if (operations instanceof DeleteReturningRepositoryOperations deleteReturningRepositoryOperations) {
                Object result = deleteReturningRepositoryOperations.deleteReturning(getDeleteReturningOperation(context, deleteEntity.get()));
                return convertIfNecessary(resultType, result);
            }
            Number result = operations.delete(getDeleteOperation(context, deleteEntity.get()));
            return convertIfNecessary(resultType, result);
        } else {
            if (operations instanceof DeleteReturningRepositoryOperations deleteReturningRepositoryOperations) {
                return (R) deleteReturningRepositoryOperations.deleteAllReturning(getDeleteReturningBatchOperation(context, deleteEntities.get()));
            }
            Number result = operations.deleteAll(getDeleteBatchOperation(context, deleteEntities.get())).orElse(0);
            return convertIfNecessary(resultType, result);
        }
    }

    private R convertIfNecessary(Argument<R> resultType, Object result) {
        if (!resultType.getType().isInstance(result)) {
            return operations.getConversionService().convertRequired(result, resultType);
        } else {
            return (R) result;
        }
    }
}
