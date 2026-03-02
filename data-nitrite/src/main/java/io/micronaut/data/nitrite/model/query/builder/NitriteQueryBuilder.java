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
 * NitriteDB query builder - legacy QueryBuilder interface wrapper. Delegates to
 * NitriteQueryBuilder2 for criteria-based operations.
 *
 * @deprecated Use NitriteQueryBuilder2 directly. This class exists for backwards compatibility.
 */
@Internal
@Deprecated
public class NitriteQueryBuilder implements QueryBuilder, QueryBuilder2 {

  private final NitriteQueryBuilder2 delegate = new NitriteQueryBuilder2();

  @Override
  public QueryResult buildInsert(
      final AnnotationMetadata repositoryMetadata, final PersistentEntity entity) {
    return delegate.buildInsert(repositoryMetadata, entity);
  }

  @Override
  public QueryResult buildInsert(
      final AnnotationMetadata repositoryMetadata, final InsertQueryDefinition definition) {
    return delegate.buildInsert(repositoryMetadata, definition);
  }

  @Override
  public QueryResult buildSelect(
      final AnnotationMetadata annotationMetadata, final SelectQueryDefinition definition) {
    return delegate.buildSelect(annotationMetadata, definition);
  }

  @Override
  public QueryResult buildUpdate(
      final AnnotationMetadata annotationMetadata, final UpdateQueryDefinition definition) {
    return delegate.buildUpdate(annotationMetadata, definition);
  }

  @Override
  public QueryResult buildDelete(
      final AnnotationMetadata annotationMetadata, final DeleteQueryDefinition definition) {
    return delegate.buildDelete(annotationMetadata, definition);
  }

  @Override
  public QueryResult buildQuery(
      @NonNull final AnnotationMetadata annotationMetadata, @NonNull final QueryModel query) {
    // This method is deprecated - criteria-based queries use QueryBuilder2
    throw new UnsupportedOperationException(
        "Use QueryBuilder2 buildSelect() instead. QueryModel API is deprecated.");
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final QueryModel query,
      @NonNull final List<String> propertiesToUpdate) {
    throw new UnsupportedOperationException(
        "Use QueryBuilder2 buildUpdate() instead. QueryModel API is deprecated.");
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final QueryModel query,
      @NonNull final Map<String, Object> propertiesToUpdate) {
    throw new UnsupportedOperationException(
        "Use QueryBuilder2 buildUpdate() instead. QueryModel API is deprecated.");
  }

  @Override
  public QueryResult buildDelete(
      @NonNull final AnnotationMetadata annotationMetadata, @NonNull final QueryModel query) {
    throw new UnsupportedOperationException(
        "Use QueryBuilder2 buildDelete() instead. QueryModel API is deprecated.");
  }

  @Override
  @NonNull
  public QueryResult buildOrderBy(
      @NonNull final PersistentEntity entity, @NonNull final Sort sort) {
    return delegate.buildOrderBy(entity, sort);
  }

  @Override
  @NonNull
  public QueryResult buildPagination(@NonNull final Pageable pageable) {
    return delegate.buildPagination(pageable);
  }

  @Override
  @NonNull
  public String buildLimitAndOffset(final long limit, final long offset) {
      return delegate.buildLimitAndOffset(limit, offset);
  }

  public boolean supportsRegex() {
      return true;
  }

  @Override
  public boolean shouldAliasProjections() {
      return true;
  }
  }
