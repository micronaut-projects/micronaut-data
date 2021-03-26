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
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Default implementation of {@link SaveAllInterceptor}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveAllInterceptor<T, R> extends AbstractQueryInterceptor<T, R>
        implements SaveAllInterceptor<T, R> {

    /**
     * Default constructor.
     * @param operations The operations
     */
    public DefaultSaveAllInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public R intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Iterable<Object> iterable = getEntitiesParameter(context, Object.class);
        Iterable<Object> rs = operations.persistAll(getInsertBatchOperation(context, iterable));
        ReturnType<R> rt = context.getReturnType();
        if (rt.isVoid()) {
            return null;
        }
        return ConversionService.SHARED.convert(rs, rt.asArgument())
                .orElseThrow(() -> new IllegalStateException("Unsupported iterable return type: " + rt.getType()));
    }
}
