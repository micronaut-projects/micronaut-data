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
package io.micronaut.data.jpa.repository.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Interceptor for JPA merge operation.
 * @param <T> The entity type
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class MergeInterceptor<T> extends AbstractQueryInterceptor<T, T> implements DataInterceptor<T, T> {

    private final JpaRepositoryOperations jpaRepositoryOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    MergeInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        this.jpaRepositoryOperations = (JpaRepositoryOperations) operations;
    }

    @Nullable
    @Override
    public T intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, T> context) {
        Object argument = context.getParameterValues()[0];
        if (argument instanceof Iterable<?> iterable) {
            List<Object> merged = new ArrayList<>();
            for (Object value : iterable) {
                if (value != null) {
                    merged.add(jpaRepositoryOperations.merge(value));
                }
            }
            return (T) convertCollectionReturnValue(argument, merged);
        }
        if (argument.getClass().isArray()) {
            int length = Array.getLength(argument);
            Object resultArray = Array.newInstance(argument.getClass().getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Object value = Array.get(argument, i);
                if (value == null) {
                    Array.set(resultArray, i, null);
                } else {
                    Array.set(resultArray, i, jpaRepositoryOperations.merge(value));
                }
            }
            return (T) resultArray;
        }
        return (T) jpaRepositoryOperations.merge(argument);
    }

    private Object convertCollectionReturnValue(Object originalArgument, Collection<Object> merged) {
        if (originalArgument instanceof List<?> list && !list.getClass().isInterface()) {
            try {
                List<Object> copy = (List<Object>) list.getClass().getDeclaredConstructor().newInstance();
                copy.addAll(merged);
                return copy;
            } catch (Exception ignored) {
                // fall through to default implementation below
            }
        }
        if (originalArgument instanceof Collection<?>) {
            return new ArrayList<>(merged);
        }
        return merged;
    }
}
