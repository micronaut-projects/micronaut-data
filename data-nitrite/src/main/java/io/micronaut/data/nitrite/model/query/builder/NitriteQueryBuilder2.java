/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import java.util.List;
import java.util.Map;

/**
 * Delegating wrapper for {@link NitriteQueryBuilder} to satisfy dynamic loading in core.
 *
 * <p>Some Micronaut Data versions look up a separate {@code *QueryBuilder2} implementation even when
 * a single class implements both {@link QueryBuilder} and {@link QueryBuilder2}. This wrapper keeps
 * Nitrite forward-compatible while remaining usable on Micronaut Data 4.x.
 *
 * <p>In Micronaut Data 5.0.x (where QueryBuilder2 is merged into QueryBuilder), this class can be
 * removed and {@link NitriteQueryBuilder} can be the only query builder entry point.
 */
@Internal
public final class NitriteQueryBuilder2 implements QueryBuilder, QueryBuilder2 {

    private final NitriteQueryBuilder delegate;

    public NitriteQueryBuilder2() {
        this.delegate = new NitriteQueryBuilder();
    }

    public NitriteQueryBuilder2(AnnotationMetadata annotationMetadata) {
        this.delegate = new NitriteQueryBuilder(annotationMetadata);
    }

    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        return delegate.buildInsert(repositoryMetadata, definition);
    }

    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        return delegate.buildInsert(repositoryMetadata, entity);
    }

    @Override
    public QueryResult buildQuery(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query) {
        return delegate.buildQuery(annotationMetadata, query);
    }

    @Override
    public QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query, @NonNull List<String> propertiesToUpdate) {
        return delegate.buildUpdate(annotationMetadata, query, propertiesToUpdate);
    }

    @Override
    public QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query, @NonNull Map<String, Object> propertiesToUpdate) {
        return delegate.buildUpdate(annotationMetadata, query, propertiesToUpdate);
    }

    @Override
    public QueryResult buildDelete(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query) {
        return delegate.buildDelete(annotationMetadata, query);
    }

    @Override
    @NonNull
    public QueryResult buildOrderBy(@NonNull PersistentEntity entity, @NonNull Sort sort) {
        return delegate.buildOrderBy(entity, sort);
    }

    @Override
    @NonNull
    public QueryResult buildPagination(@NonNull Pageable pageable) {
        return delegate.buildPagination(pageable);
    }

    @Override
    public QueryResult buildSelect(@NonNull AnnotationMetadata annotationMetadata, @NonNull SelectQueryDefinition query) {
        return delegate.buildSelect(annotationMetadata, query);
    }

    @Override
    public QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull UpdateQueryDefinition definition) {
        return delegate.buildUpdate(annotationMetadata, definition);
    }

    @Override
    public QueryResult buildDelete(@NonNull AnnotationMetadata annotationMetadata, @NonNull DeleteQueryDefinition definition) {
        return delegate.buildDelete(annotationMetadata, definition);
    }

    @Override
    @NonNull
    public String buildLimitAndOffset(long limit, long offset) {
        return delegate.buildLimitAndOffset(limit, offset);
    }

    @Override
    public boolean shouldAliasProjections() {
        return delegate.shouldAliasProjections();
    }

    @Override
    public boolean supportsForUpdate() {
        return delegate.supportsForUpdate();
    }
}
