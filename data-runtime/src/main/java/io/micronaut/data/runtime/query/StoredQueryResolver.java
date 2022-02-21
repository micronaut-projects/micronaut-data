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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.inject.ExecutableMethod;

import java.util.List;

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

    /**
     * Create stored query from provided values.
     * Used for criteria stored query creation.
     *
     * @param executableMethod   The executableMethod
     * @param operationType      The operationType
     * @param name               The name
     * @param annotationMetadata The annotation metadata
     * @param rootEntity         The root entity
     * @param query              The query
     * @param update             The update query
     * @param queryParts         The query parts
     * @param queryParameters    The query parameters
     * @param hasPageable        Has pageable
     * @param isSingleResult     Is single result
     * @param <E>                The entity type
     * @param <QR>               The result type
     * @return new instance of stored query
     */
    <E, QR> StoredQuery<E, QR> createStoredQuery(ExecutableMethod<?, ?> executableMethod,
                                                 DataMethod.OperationType operationType,
                                                 String name,
                                                 AnnotationMetadata annotationMetadata,
                                                 Class<Object> rootEntity,
                                                 String query,
                                                 String update,
                                                 String[] queryParts,
                                                 List<QueryParameterBinding> queryParameters,
                                                 boolean hasPageable,
                                                 boolean isSingleResult);

    /**
     * Create count stored query from provided values.
     * Used for criteria stored query creation.
     *
     * @param executableMethod   The executableMethod
     * @param operationType      The operationType
     * @param name               The name
     * @param annotationMetadata The annotation metadata
     * @param rootEntity         The root entity
     * @param query              The query
     * @param queryParts         The query parts
     * @param queryParameters    The query parameters
     * @return new instance of stored query
     * @return
     */
    StoredQuery<Object, Long> createCountStoredQuery(ExecutableMethod<?, ?> executableMethod,
                                                     DataMethod.OperationType operationType,
                                                     String name,
                                                     AnnotationMetadata annotationMetadata,
                                                     Class<Object> rootEntity,
                                                     String query,
                                                     String[] queryParts,
                                                     List<QueryParameterBinding> queryParameters);
}
