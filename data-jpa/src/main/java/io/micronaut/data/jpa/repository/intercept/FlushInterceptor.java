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
package io.micronaut.data.jpa.repository.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;

/**
 * Interceptor for flushing.
 * @param <T>
 */
@Internal
public class FlushInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements DataInterceptor<T, Void> {

    private final JpaRepositoryOperations jpaRepositoryOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    FlushInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        this.jpaRepositoryOperations = (JpaRepositoryOperations) operations;
    }

    @Override
    public Void intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Void> context) {
        jpaRepositoryOperations.flush();
        return null;
    }
}
