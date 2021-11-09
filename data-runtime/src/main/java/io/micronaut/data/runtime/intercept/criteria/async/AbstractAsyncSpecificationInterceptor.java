/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.intercept.criteria.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.runtime.intercept.criteria.AbstractSpecificationInterceptor;

import java.util.List;

/**
 * Abstract async specification interceptor.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 3.2
 */
public abstract class AbstractAsyncSpecificationInterceptor<T, R> extends AbstractSpecificationInterceptor<T, R> {
    protected static final Argument<List<Object>> LIST_OF_OBJECTS = Argument.listOf(Object.class);

    protected final AsyncRepositoryOperations asyncOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractAsyncSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
        if (operations instanceof AsyncCapableRepository) {
            this.asyncOperations = ((AsyncCapableRepository) operations).async();
        } else {
            throw new DataAccessException("Datastore of type [" + operations.getClass() + "] does not support asynchronous operations");
        }
    }

    protected final Argument<?> getReturnType(MethodInvocationContext<?, ?> context) {
        return findReturnType(context, Argument.OBJECT_ARGUMENT);
    }

    protected final Argument<?> findReturnType(MethodInvocationContext<?, ?> context, Argument<?> defaultArg) {
        if (context.isSuspend()) {
            return context.getReturnType().asArgument();
        }
        return context.getReturnType().asArgument().getFirstTypeVariable().orElse(defaultArg);
    }

    /**
     * Convert a number argument if necessary.
     * @param context The method context
     * @param number The number
     * @return The result
     */
    @Nullable
    protected Number convertNumberToReturnType(MethodInvocationContext<?, ?> context, Number number) {
        Argument<?> firstTypeVar = findReturnType(context, Argument.LONG);
        Class<?> type = firstTypeVar.getType();
        if (type == Object.class || type == Void.class) {
            return null;
        }
        if (number == null) {
            number = 0;
        }
        if (!type.isInstance(number)) {
            return (Number) operations.getConversionService().convert(number, firstTypeVar)
                    .orElseThrow(() -> new IllegalStateException("Unsupported number type for return type: " + firstTypeVar));
        } else {
            return number;
        }
    }
}
