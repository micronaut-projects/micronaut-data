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
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NitriteDB query builder using JPA Criteria API (QueryBuilder2). */
@Internal
public final class NitriteQueryBuilder2 implements QueryBuilder, QueryBuilder2 {

  /**
   * Placeholder prefix used to embed parameter indices in the serialized JSON query. Parsed at
   * runtime by {@code DefaultNitriteRepositoryOperations}.
   */
  public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";

  @Override
  public QueryResult buildInsert(
      final AnnotationMetadata repositoryMetadata, final InsertQueryDefinition definition) {
    throw new UnsupportedOperationException(
        "NitriteDB insert is handled directly, not via query builder");
  }

  @Override
  public QueryResult buildInsert(
      final AnnotationMetadata repositoryMetadata, final PersistentEntity entity) {
    return QueryResult.of(
        "", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
  }

  @Override
  public QueryResult buildQuery(
      @NonNull final AnnotationMetadata annotationMetadata, @NonNull final QueryModel query) {
    throw new UnsupportedOperationException("QueryModel API is deprecated.");
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final QueryModel query,
      @NonNull final List<String> propertiesToUpdate) {
    throw new UnsupportedOperationException("QueryModel API is deprecated.");
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final QueryModel query,
      @NonNull final Map<String, Object> propertiesToUpdate) {
    throw new UnsupportedOperationException("QueryModel API is deprecated.");
  }

  @Override
  public QueryResult buildDelete(
      @NonNull final AnnotationMetadata annotationMetadata, @NonNull final QueryModel query) {
    throw new UnsupportedOperationException("QueryModel API is deprecated.");
  }

  @Override
  @NonNull
  public QueryResult buildOrderBy(
      @NonNull final PersistentEntity entity, @NonNull final Sort sort) {
    return QueryResult.of(
        "", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
  }

  @Override
  @NonNull
  public QueryResult buildPagination(@NonNull final Pageable pageable) {
    return QueryResult.of(
        "", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
  }

  @Override
  public QueryResult buildSelect(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final SelectQueryDefinition query) {
    ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
    ArgumentUtils.requireNonNull("selectQueryDefinition", query);

    NitriteQueryState queryState = new NitriteQueryState(query, true);
    Map<String, Object> predicateObj = new LinkedHashMap<>();
    Map<String, Object> sortObj = new LinkedHashMap<>();

    Predicate predicate = query.predicate();
    if (predicate != null) {
      predicateObj = buildWhereClause(predicate, queryState);
    }

    List<Order> orders = query.order();
    if (!orders.isEmpty()) {
      orders.forEach(
          order -> {
            PersistentPropertyPath<?> propertyPath =
                CriteriaUtils.requireProperty(order.getExpression());
            String fieldName = NitritePredicateVisitor.getFieldName(propertyPath.getPropertyPath());
            sortObj.put(fieldName, order.isAscending() ? 1 : -1);
          });
    }

    Map<String, Object> topLevel = new LinkedHashMap<>();
    if (!predicateObj.isEmpty()) {
      topLevel.putAll(predicateObj);
    }
    if (!sortObj.isEmpty()) {
      topLevel.put("$sort", sortObj);
    }
    if (query.offset() > 0) {
      topLevel.put("$skip", query.offset());
    }
    if (query.limit() != -1) {
      topLevel.put("$limit", query.limit());
    }

    String queryString = topLevel.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(topLevel);
    return QueryResult.of(
        queryString, Collections.<String>emptyList(), queryState.getParameterBindings());
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final UpdateQueryDefinition definition) {
    NitriteQueryState queryState = new NitriteQueryState(definition, true);
    Predicate predicate = definition.predicate();
    Map<String, Object> predicateObj = new LinkedHashMap<>();
    if (predicate != null) {
      predicateObj = buildWhereClause(predicate, queryState);
    }
    String queryString =
        predicateObj.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(predicateObj);
    Map<String, String> additionalData = new HashMap<>();
    additionalData.put("update", "true");
    return QueryResult.of(
        queryString,
        Collections.<String>emptyList(),
        queryState.getParameterBindings(),
        additionalData);
  }

  @Override
  public QueryResult buildDelete(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final DeleteQueryDefinition definition) {
    NitriteQueryState queryState = new NitriteQueryState(definition, true);
    Predicate predicate = definition.predicate();
    Map<String, Object> predicateObj = new LinkedHashMap<>();
    if (predicate != null) {
      predicateObj = buildWhereClause(predicate, queryState);
    }
    String queryString =
        predicateObj.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(predicateObj);
    Map<String, String> additionalData = new HashMap<>();
    additionalData.put("delete", "true");
    return QueryResult.of(
        queryString,
        Collections.<String>emptyList(),
        queryState.getParameterBindings(),
        additionalData);
  }

  @Override
  @NonNull
  public String buildLimitAndOffset(final long limit, final long offset) {
    Map<String, Object> obj = new LinkedHashMap<>();
    if (offset > 0) {
      obj.put("$skip", offset);
    }
    if (limit > 0) {
      obj.put("$limit", limit);
    }
    return obj.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(obj);
  }

  private Map<String, Object> buildWhereClause(
      final Predicate predicate, final NitriteQueryState queryState) {
    if (predicate == null) {
      return Map.of();
    }
    Map<String, Object> queryMap = new LinkedHashMap<>();
    if (predicate instanceof IPredicate predicateVisitable) {
      predicateVisitable.visitPredicate(new NitritePredicateVisitor(queryState, queryMap));
    } else {
      throw new IllegalStateException(
          "Unsupported predicate type: " + predicate.getClass().getName());
    }
    return queryMap;
  }
}
