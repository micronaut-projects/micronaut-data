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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;

import java.util.List;

/**
 * The basic implementation of {@link StoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public class BasicStoredQuery<E, R> implements StoredQuery<E, R> {

    private final String query;
    private final String[] expandableQueryParts;
    private final List<QueryParameterBinding> queryParameterBindings;
    private final Class<E> rootEntity;
    private final Class<R> resultType;

    public BasicStoredQuery(String query, String[] expandableQueryParts, List<QueryParameterBinding> queryParameterBindings, Class<E> rootEntity, Class<R> resultType) {
        this.query = query;
        this.expandableQueryParts = expandableQueryParts == null ? new String[0] : expandableQueryParts;
        this.queryParameterBindings = queryParameterBindings;
        this.rootEntity = rootEntity;
        this.resultType = resultType;
    }

    @Override
    public String getName() {
        return "Custom query";
    }

    @Override
    public Class<E> getRootEntity() {
        return rootEntity;
    }

    @Override
    public Class<R> getResultType() {
        return resultType;
    }

    @Override
    public Argument<R> getResultArgument() {
        return Argument.of(getResultType());
    }

    @Override
    public DataType getResultDataType() {
        return DataType.ENTITY;
    }

    @Override
    public boolean hasPageable() {
        return false;
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public String[] getExpandableQueryParts() {
        return expandableQueryParts;
    }

    @Override
    public List<QueryParameterBinding> getQueryBindings() {
        return queryParameterBindings;
    }

    @Override
    public boolean useNumericPlaceholders() {
        return false;
    }

    @Override
    public boolean isCount() {
        return false;
    }

    @Override
    public boolean isSingleResult() {
        return false;
    }

    @Override
    public boolean hasResultConsumer() {
        return false;
    }
}
