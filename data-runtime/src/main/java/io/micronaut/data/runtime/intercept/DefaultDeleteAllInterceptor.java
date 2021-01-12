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
package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Collections;

/**
 * Default implementation of {@link DeleteAllInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteAllInterceptor<T> extends AbstractQueryInterceptor<T, Number> implements DeleteAllInterceptor<T> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultDeleteAllInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Number intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Number> context) {
        Argument<Number> resultType = context.getReturnType().asArgument();
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 1) {
            Object value = parameterValues[0];
            if (value instanceof Iterable) {
                DeleteBatchOperation<?> batchOperation = getDeleteBatchOperation(context, (Iterable) value);
                Number deleted = operations.deleteAll(batchOperation).orElse(0);
                return convertIfNecessary(resultType, deleted);
            } else {
                if (context.hasAnnotation(Query.class)) {
                    PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(methodKey, context);
                    Number deleted = operations.executeDelete(preparedQuery).orElse(0);
                    return convertIfNecessary(resultType, deleted);
                } else {
                    DeleteBatchOperation<?> batchOperation = getDeleteBatchOperation(context, Collections.singleton(value));
                    Number deleted = operations.deleteAll(batchOperation).orElse(0);
                    return convertIfNecessary(resultType, deleted);
                }
            }
        } else if (parameterValues.length == 0) {
            Number result = operations.deleteAll(getDeleteAllBatchOperation(context)).orElse(0);
            return convertIfNecessary(resultType, result);
        } else {
            throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
        }
    }

    private Number convertIfNecessary(Argument<Number> resultType, Number result) {
        if (!resultType.getType().isInstance(result)) {
            return ConversionService.SHARED.convert(result, resultType).orElse(0);
        } else {
            return result;
        }
    }
}
