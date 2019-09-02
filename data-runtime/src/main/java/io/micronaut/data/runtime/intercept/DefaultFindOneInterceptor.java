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
package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.model.runtime.PreparedQuery;

/**
 * Default implementation of the {@link FindOneInterceptor} interface.
 *
 * @param <T> The declaring type.
 */
public class DefaultFindOneInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements FindOneInterceptor<T> {

    /**
     * The default constructor.
     * @param datastore The operations
     */
    protected DefaultFindOneInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Object> context) {
        PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context, null);
        Object result = operations.findOne(preparedQuery);

        if (result != null) {
            ReturnType<Object> returnType = context.getReturnType();
            if (!returnType.getType().isInstance(result)) {
                return ConversionService.SHARED.convert(result, returnType.asArgument())
                            .orElseThrow(() -> new IllegalStateException("Unexpected return type: " + result));
            } else {
                return result;
            }
        } else {
            if (!isNullable(context.getAnnotationMetadata())) {
                throw new EmptyResultException();
            }
        }
        return result;
    }

}
