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
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Default implementation of {@link SaveEntityInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveEntityInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements SaveEntityInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultSaveEntityInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Object> context) {
        Object entity = getEntityParameter(context, Object.class);
        entity = operations.persist(getInsertOperation(context, entity));
        ReturnType<Object> rt = context.getReturnType();
        if (isNumber(rt.getType())) {
            return ConversionService.SHARED.convert(1, rt.asArgument())
                    .orElseThrow(() -> new IllegalStateException("Unsupported return type: " + rt.getType()));
        }
        return entity;
    }

}
