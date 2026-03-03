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
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NitriteDB query builder implementing both QueryBuilder and QueryBuilder2 for compatibility.
 * Primary implementation uses JPA Criteria (QueryBuilder2) to align with Micronaut Data 5.0.x.
 *
 * <h2>Design notes</h2>
 *
 * <ul>
 *   <li>Micronaut Data’s annotation processor uses {@link QueryResult#getAdditionalRequiredParameters()}
 *       to determine which <em>repository method parameters</em> must be present so it can bind them
 *       into the generated query. This is a compile-time contract. Do not use that map as a generic
 *       metadata channel (for example, do not put markers like {@code "update" -> "true"} or {@code
 *       "delete" -> "true"}) — it will cause compilation failures for implicit {@code CrudRepository}
 *       methods when {@code implicitQueries=true}.</li>
 *   <li>Update queries are serialized as a JSON object that may include a {@code "$set"} key. The
 *       values inside {@code "$set"} must be encoded as {@link #QUERY_PARAMETER_PLACEHOLDER}
 *       placeholders (for bindable parameters) so the runtime can bind them from
 *       {@link io.micronaut.data.model.runtime.PreparedQuery#getParameterArray()}.</li>
 * </ul>
 */
@Internal
@Introspected
@TypeHint(NitriteQueryBuilder.class)
public final class NitriteQueryBuilder implements QueryBuilder, QueryBuilder2 {

  /**
   * Placeholder prefix used to embed parameter indices in the serialized JSON query. Parsed at
   * runtime by {@code DefaultNitriteRepositoryOperations}.
   *
   * <p>Format: {@code "$mn_qp:<index>"} where {@code <index>} is the position of the
   * {@link io.micronaut.data.model.query.builder.QueryParameterBinding} in the prepared query.
   */
  public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";

  public NitriteQueryBuilder() {}

  public NitriteQueryBuilder(AnnotationMetadata annotationMetadata) {}

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
    // Provide minimal support for QueryModel to avoid AP errors, but prefer buildSelect (Criteria)
    return QueryResult.of(
        "{}", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final QueryModel query,
      @NonNull final List<String> propertiesToUpdate) {
    return QueryResult.of(
        "{}", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final QueryModel query,
      @NonNull final Map<String, Object> propertiesToUpdate) {
    return QueryResult.of(
        "{}", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
  }

  @Override
  public QueryResult buildDelete(
      @NonNull final AnnotationMetadata annotationMetadata, @NonNull final QueryModel query) {
    return QueryResult.of(
        "{}", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
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

  // --- QueryBuilder2 (Criteria API) implementation ---

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

    Map<String, Object> setObj = new LinkedHashMap<>();
    Map<String, Object> propertiesToUpdate = definition.propertiesToUpdate();
    if (propertiesToUpdate != null) {
      for (Map.Entry<String, Object> entry : propertiesToUpdate.entrySet()) {
        String fieldName = entry.getKey();
        Object value = entry.getValue();
        if (value instanceof BindingParameter bindingParameter) {
          PersistentProperty property = definition.persistentEntity().getPropertyByName(fieldName);
          io.micronaut.data.model.PersistentPropertyPath propertyPath =
              property != null ? new io.micronaut.data.model.PersistentPropertyPath(property) : null;
          int index =
              queryState.pushParameter(
                  bindingParameter, NitritePredicateVisitor.newBindingContext(propertyPath));
          setObj.put(fieldName, QUERY_PARAMETER_PLACEHOLDER + ":" + index);
        } else if (value instanceof String s && s.startsWith(QUERY_PARAMETER_PLACEHOLDER)) {
          setObj.put(fieldName, s);
        } else {
          setObj.put(fieldName, value);
        }
      }
    }

    Map<String, Object> topLevel = new LinkedHashMap<>();
    if (!predicateObj.isEmpty()) {
      topLevel.putAll(predicateObj);
    }
    if (!setObj.isEmpty()) {
      topLevel.put("$set", setObj);
    }

    String queryString = topLevel.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(topLevel);
    // Note: QueryResult additionalRequiredParameters is used by the annotation processor to bind
    // method parameters. It must not be used for metadata markers like "update=true" (that breaks
    // implicit CrudRepository method generation).
    return QueryResult.of(
        queryString, Collections.<String>emptyList(), queryState.getParameterBindings());
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
    return QueryResult.of(
        queryString,
        Collections.<String>emptyList(),
        queryState.getParameterBindings());
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

  public boolean supportsRegex() {
    return true;
  }

  @Override
  public boolean shouldAliasProjections() {
    return true;
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
