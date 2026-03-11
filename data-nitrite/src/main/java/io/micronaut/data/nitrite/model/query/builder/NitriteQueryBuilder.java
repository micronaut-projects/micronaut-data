/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the \"License\");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an \"AS IS\" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NitriteDB query builder implementing both QueryBuilder and QueryBuilder2 for compatibility.
 * Primary implementation uses JPA Criteria (QueryBuilder2) to align with Micronaut Data 5.0.x.
 */
@Internal
@Introspected
@TypeHint(NitriteQueryBuilder.class)
public final class NitriteQueryBuilder implements QueryBuilder, QueryBuilder2 {

  public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";
  private static final Logger LOG = LoggerFactory.getLogger(NitriteQueryBuilder.class);

  public NitriteQueryBuilder() {
  }

  public NitriteQueryBuilder(AnnotationMetadata annotationMetadata) {
  }

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
    ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
    ArgumentUtils.requireNonNull("query", query);

    NitriteQueryState queryState = new NitriteQueryState(query.getPersistentEntity(), true);
    Map<String, Object> predicateObj = buildWhereClauseFromQueryModel(query.getCriteria(), queryState);
    
    if (LOG.isDebugEnabled()) {
        LOG.debug("buildQuery: entity={}, criteria={}, predicateObj={}", query.getPersistentEntity().getName(), query.getCriteria(), predicateObj);
    }

    Sort sort = query.getSort();
    Map<String, Object> sortObj = new LinkedHashMap<>();
    if (sort.isSorted()) {
        for (Sort.Order order : sort.getOrderBy()) {
            sortObj.put(order.getProperty(), order.isAscending() ? 1 : -1);
        }
    }

    Map<String, Object> topLevel = new LinkedHashMap<>();
    if (!predicateObj.isEmpty()) {
      topLevel.putAll(predicateObj);
    }
    if (!sortObj.isEmpty()) {
      topLevel.put("$sort", sortObj);
    }
    if (query.getOffset() > 0) {
      topLevel.put("$skip", (int) query.getOffset());
    }
    if (query.getMax() != -1) {
      topLevel.put("$limit", query.getMax());
    }

    String queryString = topLevel.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(topLevel);
    return QueryResult.of(
        queryString, Collections.emptyList(), queryState.getParameterBindings());
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final QueryModel query,
      @NonNull final List<String> propertiesToUpdate) {
    throw new IllegalStateException(
        "Only 'buildUpdate' with 'Map<String, Object> propertiesToUpdate' is supported");
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final QueryModel query,
      @NonNull final Map<String, Object> propertiesToUpdate) {
    ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
    ArgumentUtils.requireNonNull("query", query);
    ArgumentUtils.requireNonNull("propertiesToUpdate", propertiesToUpdate);

    NitriteQueryState queryState = new NitriteQueryState(query.getPersistentEntity(), true);
    Map<String, Object> predicateObj = buildWhereClauseFromQueryModel(query.getCriteria(), queryState);

    Map<String, Object> setObj = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : propertiesToUpdate.entrySet()) {
      String fieldName = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof BindingParameter bindingParameter) {
        PersistentProperty property = query.getPersistentEntity().getPropertyByName(fieldName);
        io.micronaut.data.model.PersistentPropertyPath propertyPath =
            property != null ? io.micronaut.data.model.PersistentPropertyPath.of(Collections.emptyList(), property, property.getName()) : null;
        int index =
            queryState.pushParameter(
                bindingParameter, NitritePredicateVisitor.newBindingContext(propertyPath));
        setObj.put(fieldName, QUERY_PARAMETER_PLACEHOLDER + ":" + index);
      } else {
        setObj.put(fieldName, value);
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
    return QueryResult.of(
        queryString, Collections.emptyList(), queryState.getParameterBindings());
  }

  @Override
  public QueryResult buildDelete(
      @NonNull final AnnotationMetadata annotationMetadata, @NonNull final QueryModel query) {
    ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
    ArgumentUtils.requireNonNull("query", query);

    NitriteQueryState queryState = new NitriteQueryState(query.getPersistentEntity(), true);
    Map<String, Object> predicateObj = buildWhereClauseFromQueryModel(query.getCriteria(), queryState);
    String queryString =
        predicateObj.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(predicateObj);
    return QueryResult.of(
        queryString, Collections.emptyList(), queryState.getParameterBindings());
  }

  private Map<String, Object> buildWhereClauseFromQueryModel(
      final QueryModel.Junction criteria, final NitriteQueryState queryState) {
    if (criteria == null || criteria.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> queryMap = new LinkedHashMap<>();
    handleJunction(queryMap, criteria, queryState);
    return queryMap;
  }

  private void handleJunction(Map<String, Object> queryMap, QueryModel.Junction junction, NitriteQueryState queryState) {
    String operator = junction instanceof QueryModel.Conjunction ? "$and" : "$or";
    List<Object> criteriaList = new ArrayList<>();
    for (QueryModel.Criterion criterion : junction.getCriteria()) {
        Map<String, Object> criterionMap = new LinkedHashMap<>();
        handleCriterion(criterionMap, criterion, queryState);
        if (!criterionMap.isEmpty()) {
            criteriaList.add(criterionMap);
        }
    }
    if (criteriaList.size() == 1) {
        queryMap.putAll((Map<String, Object>) criteriaList.get(0));
    } else if (!criteriaList.isEmpty()) {
        queryMap.put(operator, criteriaList);
    }
  }

  private void handleCriterion(Map<String, Object> queryMap, QueryModel.Criterion criterion, NitriteQueryState queryState) {
    if (LOG.isDebugEnabled()) {
        LOG.debug("handleCriterion: criterion={}", criterion.getClass().getSimpleName());
    }
    if (criterion instanceof QueryModel.Junction junction) {
        handleJunction(queryMap, junction, queryState);
    } else if (criterion instanceof QueryModel.PropertyCriterion pc) {
        String propertyName = pc.getProperty();
        Object value = pc.getValue();
        if (LOG.isDebugEnabled()) {
            LOG.debug("handleCriterion: propertyName={}, value={}", propertyName, value);
        }
        io.micronaut.data.model.PersistentPropertyPath propertyPath = queryState.getEntity().getPropertyPath(propertyName);
        String fieldName = propertyName;
        if (propertyPath != null) {
            fieldName = NitritePredicateVisitor.getFieldName(propertyPath);
            if (LOG.isDebugEnabled()) {
                LOG.debug("handleCriterion: fieldName from propertyPath={}", fieldName);
            }
        }

        Object valueRep;
        if (value instanceof BindingParameter bp) {
            int index = queryState.pushParameter(bp, NitritePredicateVisitor.newBindingContext(propertyPath));
            valueRep = QUERY_PARAMETER_PLACEHOLDER + ":" + index;
        } else {
            valueRep = value;
        }

        if (criterion instanceof QueryModel.Equals) {
            queryMap.put(fieldName, valueRep);
        } else if (criterion instanceof QueryModel.NotEquals) {
            queryMap.put(fieldName, Map.of("$ne", valueRep));
        } else if (criterion instanceof QueryModel.GreaterThan) {
            queryMap.put(fieldName, Map.of("$gt", valueRep));
        } else if (criterion instanceof QueryModel.GreaterThanEquals) {
            queryMap.put(fieldName, Map.of("$gte", valueRep));
        } else if (criterion instanceof QueryModel.LessThan) {
            queryMap.put(fieldName, Map.of("$lt", valueRep));
        } else if (criterion instanceof QueryModel.LessThanEquals) {
            queryMap.put(fieldName, Map.of("$lte", valueRep));
        } else if (criterion instanceof QueryModel.In) {
            queryMap.put(fieldName, Map.of("$in", valueRep));
        } else if (criterion instanceof QueryModel.NotIn) {
            queryMap.put(fieldName, Map.of("$nin", valueRep));
        } else if (criterion instanceof QueryModel.IsNull) {
            queryMap.put(fieldName, null);
        } else if (criterion instanceof QueryModel.IsNotNull) {
            queryMap.put(fieldName, Map.of("$ne", null));
        } else if (criterion instanceof QueryModel.IdEquals) {
            queryMap.put(NitritePredicateVisitor.ID_FIELD, valueRep);
        }
    }
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
    if (LOG.isDebugEnabled()) {
        LOG.debug("buildSelect: entity={}, predicate={}", query.persistentEntity().getName(), query.predicate());
    }
    NitriteQueryState queryState = new NitriteQueryState(query.persistentEntity(), true);
    Map<String, Object> predicateObj = new LinkedHashMap<>();
    Map<String, Object> sortObj = new LinkedHashMap<>();

    Predicate predicate = query.predicate();
    if (predicate != null) {
      predicateObj = buildWhereClauseFromCriteria(predicate, queryState);
    }

    List<Order> orders = query.order();
    if (!orders.isEmpty()) {
      orders.forEach(
          order -> {
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
    return QueryResult.of(
        queryString, Collections.<String>emptyList(), queryState.getParameterBindings());
  }

  @Override
  public QueryResult buildUpdate(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final UpdateQueryDefinition definition) {
    NitriteQueryState queryState = new NitriteQueryState(definition.persistentEntity(), true);
    Predicate predicate = definition.predicate();
    Map<String, Object> predicateObj = new LinkedHashMap<>();
    if (predicate != null) {
      predicateObj = buildWhereClauseFromCriteria(predicate, queryState);
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
              property != null ? io.micronaut.data.model.PersistentPropertyPath.of(Collections.emptyList(), property, property.getName()) : null;
          int index =
              queryState.pushParameter(
                  bindingParameter, NitritePredicateVisitor.newBindingContext(propertyPath));
          setObj.put(fieldName, QUERY_PARAMETER_PLACEHOLDER + ":" + index);
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
    return QueryResult.of(
        queryString, Collections.<String>emptyList(), queryState.getParameterBindings());
  }

  @Override
  public QueryResult buildDelete(
      @NonNull final AnnotationMetadata annotationMetadata,
      @NonNull final DeleteQueryDefinition definition) {
    NitriteQueryState queryState = new NitriteQueryState(definition.persistentEntity(), true);
    Predicate predicate = definition.predicate();
    Map<String, Object> predicateObj = new LinkedHashMap<>();
    if (predicate != null) {
      predicateObj = buildWhereClauseFromCriteria(predicate, queryState);
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
      obj.put("$skip", (int) offset);
    }
    if (limit > 0) {
      obj.put("$limit", (int) limit);
    }
    return obj.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(obj);
  }

  public boolean supportsRegex() {
    return true;
  }

  private Map<String, Object> buildWhereClauseFromCriteria(
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
