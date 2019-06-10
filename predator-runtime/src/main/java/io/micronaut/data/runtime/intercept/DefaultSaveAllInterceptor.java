/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Default implementation of {@link SaveAllInterceptor}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveAllInterceptor<T, R> extends AbstractQueryInterceptor<T, Iterable<R>>
        implements SaveAllInterceptor<T, R> {

    /**
     * Default constructor.
     * @param operations The operations
     */
    public DefaultSaveAllInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Iterable<R> intercept(MethodInvocationContext<T, Iterable<R>> context) {
        Object[] parameterValues = context.getParameterValues();
        if (ArrayUtils.isNotEmpty(parameterValues) && parameterValues[0] instanceof Iterable) {
            //noinspection unchecked
            Iterable<R> iterable = (Iterable<R>) parameterValues[0];
            Iterable<R> rs = operations.persistAll(getBatchOperation(context, iterable));
            ReturnType<Iterable<R>> rt = context.getReturnType();
            if (!rt.getType().isInstance(rs)) {
                return ConversionService.SHARED.convert(rs, rt.asArgument())
                            .orElseThrow(() -> new IllegalStateException("Unsupported iterable return type: " + rs.getClass()));
            }
            return rs;
        } else {
            throw new IllegalArgumentException("First argument should be an iterable");
        }
    }
}
