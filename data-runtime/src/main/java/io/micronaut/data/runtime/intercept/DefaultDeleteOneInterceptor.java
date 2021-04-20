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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Optional;

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
        Class<Object> returnType = context.getReturnType().getType();
        Optional<Object> deleteEntity = findEntityParameter(context, Object.class);
        if (deleteEntity.isPresent()) {
            Object entity = deleteEntity.get();
            Class<?> rootEntity = getRequiredRootEntity(context);
            if (!rootEntity.isInstance(entity)) {
                throw new IllegalArgumentException("Entity argument must be an instance of " + rootEntity.getName());
            }
            Number deleted = operations.delete(getDeleteOperation(context, entity));
            if (isNumber(returnType)) {
                return ConversionService.SHARED.convertRequired(deleted, returnType);
            } else if (returnType.equals(rootEntity)) {
                if (deleted.intValue() > 0) {
                    return entity;
                } else {
                    return null;
                }
            }
            return null;
        }
        throw new IllegalStateException("Argument not found");
    }

}
