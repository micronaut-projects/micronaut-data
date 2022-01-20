/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.query;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.model.runtime.StoredQuery;

/**
 * Stored query resolver.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
public interface StoredQueryResolver {

    /**
     * Stored query resolved from the method context.
     *
     * @param context     The method context
     * @param entityClass The entity type
     * @param resultType  The result type
     * @param <E>         The entity type
     * @param <R>         The result type
     * @return The prepared query
     */
    <E, R> StoredQuery<E, R> resolveQuery(MethodInvocationContext<?, ?> context, Class<E> entityClass, Class<R> resultType);

    /**
     * Stored count query resolved from the method context.
     *
     * @param context     The method context
     * @param entityClass The entity type
     * @param resultType  The result type
     * @param <E>         The entity type
     * @param <R>         The result type
     * @return The prepared query
     */
    <E, R> StoredQuery<E, R> resolveCountQuery(MethodInvocationContext<?, ?> context, Class<E> entityClass, Class<R> resultType);

}
