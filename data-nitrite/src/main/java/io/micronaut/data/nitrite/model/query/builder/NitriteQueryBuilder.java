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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NitriteDB query builder implementing QueryBuilder for Micronaut Data 5.0.x.
 * Uses JPA Criteria API for building queries.
 */
@Internal
@Introspected
@TypeHint(NitriteQueryBuilder.class)
public final class NitriteQueryBuilder implements QueryBuilder {

  /**
   * The query parameter placeholder.
   */
  public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";

  /**
   * Default constructor.
   */
  public NitriteQueryBuilder() {
  }

  /**
   * Constructor with annotation metadata.
   *
   * @param annotationMetadata the annotation metadata
   */
  public NitriteQueryBuilder(AnnotationMetadata annotationMetadata) {
  }

  @Override
  public QueryResult buildInsert(
      final AnnotationMetadata repositoryMetadata, final InsertQueryDefinition definition) {
    return QueryResult.of(
        "", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
  }

  @Override
  @SuppressWarnings("unchecked")
  public QueryResult buildSelect(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final SelectQueryDefinition query) {
    ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
    ArgumentUtils.requireNonNull("selectQueryDefinition", query);

    NitriteQueryState queryState = new NitriteQueryState(query, true);
    Map<String, Object> predicateObj = new LinkedHashMap<>();
    Map<String, Object> sortObj = new LinkedHashMap<>();

    Object p = query.predicate();
    if (p != null) {
      predicateObj = buildWhereClause(p, queryState);
    }

    Object o = query.order();
    List<?> orders = (List<?>) o;
    if (orders != null && !orders.isEmpty()) {
      orders.forEach(
          orderItem -> {
            // Using jakarta types because the annotation processor/model module
            // currently returns jakarta implementations in this branch.
            jakarta.persistence.criteria.Order order = (jakarta.persistence.criteria.Order) orderItem;
            io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath =
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
    if (queryString.contains("$strLenCP") || queryString.contains("$multiply")) {
        throw new UnsupportedOperationException("NitriteDB does not support MongoDB-only operators: " + queryString);
    }
    return QueryResult.of(
        queryString, Collections.<String>emptyList(), queryState.getParameterBindings());
  }

  @Override
  @SuppressWarnings("unchecked")
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final UpdateQueryDefinition definition) {
    ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
    ArgumentUtils.requireNonNull("updateQueryDefinition", definition);

    NitriteQueryState queryState = new NitriteQueryState(definition, false);
    Map<String, Object> updateObj = new LinkedHashMap<>();
    Map<String, Object> setClause = new LinkedHashMap<>();

    definition
        .propertiesToUpdate()
        .forEach(
            (prop, value) -> {
              if (value instanceof BindingParameter bindingParameter) {
                BindingParameter.BindingContext context = BindingParameter.BindingContext.create();
                setClause.put(prop, QUERY_PARAMETER_PLACEHOLDER + ":" + queryState.pushParameter(bindingParameter, context));
              } else {
                setClause.put(prop, value);
              }
            });

    if (!setClause.isEmpty()) {
      updateObj.put("$set", setClause);
    }

    Object p = definition.predicate();
    if (p != null) {
      updateObj.putAll(buildWhereClause(p, queryState));
    }

    return QueryResult.of(
        NitritePredicateVisitor.toJsonString(updateObj),
        Collections.<String>emptyList(),
        queryState.getParameterBindings());
  }

  @Override
  public QueryResult buildDelete(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final DeleteQueryDefinition definition) {
    ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
    ArgumentUtils.requireNonNull("deleteQueryDefinition", definition);

    NitriteQueryState queryState = new NitriteQueryState(definition, false);
    Map<String, Object> deleteObj = new LinkedHashMap<>();

    Object p = definition.predicate();
    if (p != null) {
      deleteObj.putAll(buildWhereClause(p, queryState));
    }

    return QueryResult.of(
        deleteObj.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(deleteObj),
        Collections.<String>emptyList(),
        queryState.getParameterBindings());
  }

  @Override
  public String buildLimitAndOffset(long limit, long offset) {
    return "";
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> buildWhereClause(Object predicate, NitriteQueryState queryState) {
    NitritePredicateVisitor visitor = new NitritePredicateVisitor(queryState);
    // Cast to IPredicate (common interface) and call visitPredicate
    ((io.micronaut.data.model.jpa.criteria.IPredicate) predicate).visitPredicate(visitor);
    return visitor.getQuery();
  }
}
