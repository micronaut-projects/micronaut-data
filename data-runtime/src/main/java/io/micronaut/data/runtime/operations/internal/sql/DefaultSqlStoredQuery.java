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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SqlStoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class DefaultSqlStoredQuery<E, R> extends DefaultBindableParametersStoredQuery<E, R> implements SqlStoredQuery<E, R> {

    private final boolean expandableQuery;
    private final SqlQueryBuilder queryBuilder;

    /**
     * @param storedQuery             The stored query
     * @param runtimePersistentEntity The persistent entity
     * @param queryBuilder            The query builder
     */
    public DefaultSqlStoredQuery(StoredQuery<E, R> storedQuery, RuntimePersistentEntity<E> runtimePersistentEntity, SqlQueryBuilder queryBuilder) {
        super(storedQuery, runtimePersistentEntity);
        this.queryBuilder = queryBuilder;
        Objects.requireNonNull(storedQuery, "Query cannot be null");
        Objects.requireNonNull(queryBuilder, "Builder cannot be null");
        String[] expandableQueryParts = storedQuery.getExpandableQueryParts();
        List<QueryParameterBinding> queryParameterBindings = storedQuery.getQueryBindings();
        this.expandableQuery = expandableQueryParts.length > 1 && queryParameterBindings.stream().anyMatch(QueryParameterBinding::isExpandable);
        if (expandableQuery && expandableQueryParts.length != queryParameterBindings.size() + 1) {
            throw new IllegalStateException("Expandable query parts size should be the same as parameters size + 1. " + expandableQueryParts.length + " != 1 + " + queryParameterBindings.size() + " " + storedQuery.getQuery() + " " + Arrays.toString(expandableQueryParts));
        }
    }

    @Override
    public boolean isExpandableQuery() {
        return expandableQuery;
    }

    @Override
    public Dialect getDialect() {
        return queryBuilder.getDialect();
    }

    @Override
    public SqlQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    @Override
    public Map<QueryParameterBinding, Object> collectAutoPopulatedPreviousValues(E entity) {
        StoredQuery<E, R> storedQuery = getStoredQueryDelegate();
        if (storedQuery.getQueryBindings().isEmpty()) {
            return null;
        }
        return storedQuery.getQueryBindings().stream()
            .filter(b -> b.isAutoPopulated() && b.isRequiresPreviousPopulatedValue())
            .map(b -> {
                if (b.getPropertyPath() == null) {
                    throw new IllegalStateException("Missing property path for query parameter: " + b);
                }
                Object value = entity;
                for (String property : b.getPropertyPath()) {
                    if (value == null) {
                        break;
                    }
                    value = BeanWrapper.getWrapper(value).getRequiredProperty(property, Argument.OBJECT_ARGUMENT);
                }
                return new AbstractMap.SimpleEntry<>(b, value);
            })
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

}
