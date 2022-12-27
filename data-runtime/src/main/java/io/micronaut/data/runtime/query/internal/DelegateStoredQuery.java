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
package io.micronaut.data.runtime.query.internal;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.transaction.TransactionDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Delegate implementation of {@link StoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.3.
 */
public interface DelegateStoredQuery<E, R> extends StoredQuery<E, R> {

    /**
     * @return The delegate
     */
    StoredQuery<E, R> getStoredQueryDelegate();

    @Override
    default AnnotationMetadata getAnnotationMetadata() {
        return getStoredQueryDelegate().getAnnotationMetadata();
    }

    @Override
    default Class<E> getRootEntity() {
        return getStoredQueryDelegate().getRootEntity();
    }

    @Override
    default boolean hasPageable() {
        return getStoredQueryDelegate().hasPageable();
    }

    @Override
    default String getQuery() {
        return getStoredQueryDelegate().getQuery();
    }

    @Override
    default String[] getExpandableQueryParts() {
        return getStoredQueryDelegate().getExpandableQueryParts();
    }

    @Override
    default List<QueryParameterBinding> getQueryBindings() {
        return getStoredQueryDelegate().getQueryBindings();
    }

    @Override
    default Class<R> getResultType() {
        return getStoredQueryDelegate().getResultType();
    }

    @Override
    default Optional<TransactionDefinition> getTransactionDefinition() {
        return getStoredQueryDelegate().getTransactionDefinition();
    }

    @Override
    default Argument<R> getResultArgument() {
        return getStoredQueryDelegate().getResultArgument();
    }

    @Override
    default DataType getResultDataType() {
        return getStoredQueryDelegate().getResultDataType();
    }

    @Override
    default boolean isNative() {
        return getStoredQueryDelegate().isNative();
    }

    @Override
    default boolean useNumericPlaceholders() {
        return getStoredQueryDelegate().useNumericPlaceholders();
    }

    @Override
    default boolean isDtoProjection() {
        return getStoredQueryDelegate().isDtoProjection();
    }

    @Override
    default Optional<Class<?>> getEntityIdentifierType() {
        return getStoredQueryDelegate().getEntityIdentifierType();
    }

    @Override
    default Class<?>[] getArgumentTypes() {
        return getStoredQueryDelegate().getArgumentTypes();
    }

    @Override
    default boolean isCount() {
        return getStoredQueryDelegate().isCount();
    }

    @Override
    default Map<String, Object> getQueryHints() {
        return getStoredQueryDelegate().getQueryHints();
    }

    @Override
    default Set<JoinPath> getJoinFetchPaths() {
        return getStoredQueryDelegate().getJoinFetchPaths();
    }

    @Override
    default boolean isSingleResult() {
        return getStoredQueryDelegate().isSingleResult();
    }

    @Override
    default boolean hasResultConsumer() {
        return getStoredQueryDelegate().hasResultConsumer();
    }

    @Override
    default boolean isOptimisticLock() {
        return getStoredQueryDelegate().isOptimisticLock();
    }

    @Override
    default String getName() {
        return getStoredQueryDelegate().getName();
    }

    @Nullable
    @Override
    default String[] getIndexedParameterAutoPopulatedPropertyPaths() {
        return getStoredQueryDelegate().getIndexedParameterAutoPopulatedPropertyPaths();
    }

    @Override
    default String[] getIndexedParameterAutoPopulatedPreviousPropertyPaths() {
        return getStoredQueryDelegate().getIndexedParameterAutoPopulatedPreviousPropertyPaths();
    }

    @Override
    default int[] getIndexedParameterAutoPopulatedPreviousPropertyIndexes() {
        return getStoredQueryDelegate().getIndexedParameterAutoPopulatedPreviousPropertyIndexes();
    }

    @Override
    default boolean isRawQuery() {
        return getStoredQueryDelegate().isRawQuery();
    }
}
