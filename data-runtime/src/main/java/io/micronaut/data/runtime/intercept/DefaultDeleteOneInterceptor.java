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
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * The default implementation of {@link DeleteOneInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteOneInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements DeleteOneInterceptor<T> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultDeleteOneInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Object> context) {
        Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length == 1) {
            Object o = parameterValues[0];
            Number deleted;
            final Class<Object> returnType = context.getReturnType().getType();
            if (o instanceof Iterable) {
                DeleteBatchOperation<?> batchOperation = getDeleteBatchOperation(context, (Iterable) o);
                deleted = operations.deleteAll(batchOperation).orElse(null);
            } else {
                if (o == null) {
                    throw new IllegalArgumentException("Entity to delete cannot be null");
                }
                Class<?> rootEntity = getRequiredRootEntity(context);
                if (!rootEntity.isInstance(o)) {
                    throw new IllegalArgumentException("Entity argument must be an instance of " + rootEntity.getName());
                }
                deleted = operations.delete(getDeleteOperation(context, o));
                if (returnType.equals(rootEntity)) {
                    if (deleted.intValue() > 0) {
                        return o;
                    } else {
                        return null;
                    }
                }
            }
            if (Number.class.isAssignableFrom(returnType)) {
                return ConversionService.SHARED.convertRequired(deleted, returnType);
            }
            return null;
        } else {
            throw new IllegalStateException("Expected exactly one argument");
        }
    }

}
