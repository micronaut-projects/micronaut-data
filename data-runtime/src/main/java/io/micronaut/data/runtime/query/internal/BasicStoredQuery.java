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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
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

    private final String name;
    private final AnnotationMetadata annotationMetadata;
    private final String query;
    private final String[] expandableQueryParts;
    private final List<QueryParameterBinding> queryParameterBindings;
    private final Class<E> rootEntity;
    private final Class<R> resultType;
    private final boolean pageable;
    private final boolean isSingleResult;
    private final boolean isCount;
    private final DataType resultDataType;
    private final boolean rawQuery;

    public BasicStoredQuery(String query,
                            String[] expandableQueryParts,
                            List<QueryParameterBinding> queryParameterBindings,
                            Class<E> rootEntity,
                            Class<R> resultType) {
        this("Custom query", AnnotationMetadata.EMPTY_METADATA, query, expandableQueryParts, queryParameterBindings, rootEntity, resultType, false, false, false);
    }

    public BasicStoredQuery(String name,
                            AnnotationMetadata annotationMetadata,
                            String query,
                            String[] expandableQueryParts,
                            List<QueryParameterBinding> queryParameterBindings,
                            Class<E> rootEntity,
                            Class<R> resultType,
                            boolean pageable,
                            boolean isSingleResult,
                            boolean isCount) {
        this.name = name;
        this.annotationMetadata = annotationMetadata;
        this.query = query;
        this.expandableQueryParts = expandableQueryParts == null ? new String[0] : expandableQueryParts;
        this.queryParameterBindings = queryParameterBindings;
        this.rootEntity = rootEntity;
        this.resultType = resultType;
        this.pageable = pageable;
        this.isSingleResult = isSingleResult;
        this.isCount = isCount;
        this.resultDataType = isCount ? DataType.forType(resultType) : DataType.ENTITY;
        this.rawQuery = annotationMetadata.stringValue(Query.class, DataMethod.META_MEMBER_RAW_QUERY).isPresent();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public String getName() {
        return name;
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
        return resultDataType;
    }

    @Override
    public boolean hasPageable() {
        return pageable;
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
        return annotationMetadata.classValue(RepositoryConfiguration.class, "queryBuilder")
                .map(c -> c == SqlQueryBuilder.class).orElse(false);
    }

    @Override
    public boolean isCount() {
        return isCount;
    }

    @Override
    public boolean isSingleResult() {
        return isSingleResult;
    }

    @Override
    public boolean hasResultConsumer() {
        return false;
    }

    @Override
    public boolean isRawQuery() {
        return this.rawQuery;
    }
}
