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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.UpdateAllEntitiesInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Default implementation of {@link UpdateAllEntitiesInterceptor}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 2.4.0
 */
public class DefaultUpdateAllEntitiesInterceptor<T, R> extends AbstractQueryInterceptor<T, R>
        implements UpdateAllEntitiesInterceptor<T, R> {

    /**
     * Default constructor.
     * @param operations The operations
     */
    public DefaultUpdateAllEntitiesInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public R intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Iterable<R> iterable = (Iterable<R>) getEntitiesParameter(context, Object.class);
        //noinspection unchecked
        Class<R> rootEntity = getRequiredRootEntity(context);
        Iterable<R> rs = operations.updateAll(getUpdateAllBatchOperation(context, rootEntity, iterable));
        ReturnType<R> rt = context.getReturnType();
        if (rt.isVoid()) {
            return null;
        }
        if (isNumber(rt.getType())) {
            return operations.getConversionService().convert(count(rs), rt.asArgument())
                        .orElseThrow(() -> new IllegalStateException("Unsupported return type: " + rt.getType()));
        }
        return operations.getConversionService().convert(rs, rt.asArgument())
                    .orElseThrow(() -> new IllegalStateException("Unsupported iterable return type: " + rt.getType()));
    }
}
