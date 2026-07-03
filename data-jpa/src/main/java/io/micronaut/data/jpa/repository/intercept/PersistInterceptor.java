/*
 * Copyright 2017-2025 original authors
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

import java.lang.reflect.Array;

/**
 * Interceptor for {@code jakarta.data.repository.stateful.Persist} methods.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class PersistInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements DataInterceptor<T, Void> {

    private final JpaRepositoryOperations jpaRepositoryOperations;

    PersistInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        this.jpaRepositoryOperations = (JpaRepositoryOperations) operations;
    }

    @Override
    public Void intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Void> context) {
        Object argument = context.getParameterValues()[0];
        if (argument == null) {
            return null;
        }
        if (argument instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                jpaRepositoryOperations.persist(value);
            }
            return null;
        }
        if (argument.getClass().isArray()) {
            int length = Array.getLength(argument);
            for (int i = 0; i < length; i++) {
                Object value = Array.get(argument, i);
                jpaRepositoryOperations.persist(value);
            }
            return null;
        }
        jpaRepositoryOperations.persist(argument);
        return null;
    }
}
