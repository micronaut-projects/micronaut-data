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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NitriteDB query builder implementing both QueryBuilder and QueryBuilder2 for compatibility.
 * Primary implementation uses JPA Criteria (QueryBuilder2) to align with Micronaut Data 5.0.x.
 */
@Internal
@Introspected
@TypeHint(NitriteQueryBuilder.class)
public final class NitriteQueryBuilder implements QueryBuilder {

    /**
     * Query parameter placeholder prefix.
     */
    public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";
    private static final Logger LOG = LoggerFactory.getLogger(NitriteQueryBuilder.class);

    /**
     * Creates a new NitriteQueryBuilder.
     */
    public NitriteQueryBuilder() {
    }

    /**
     * Creates a new NitriteQueryBuilder with annotation metadata.
     *
     * @param annotationMetadata the annotation metadata
     */
    public NitriteQueryBuilder(AnnotationMetadata annotationMetadata) {
    }

    @Override
    public QueryResult buildInsert(
        final AnnotationMetadata repositoryMetadata, final InsertQueryDefinition definition) {
        return QueryResult.of("", Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    @Override
    public QueryResult buildSelect(
        @NonNull final AnnotationMetadata annotationMetadata,
        @NonNull final SelectQueryDefinition query) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("buildSelect: entity={}, predicate={}", query.persistentEntity().getName(), query.predicate());
        }
        NitriteQueryState queryState = new NitriteQueryState(query.persistentEntity());
        Map<String, Object> predicateObj = new LinkedHashMap<>();
        Map<String, Object> group = new LinkedHashMap<>();
        Map<String, Object> countObj = new LinkedHashMap<>();
        Map<String, Object> sortObj = new LinkedHashMap<>();

        Predicate predicate = query.predicate();
        if (predicate != null) {
            predicateObj = buildWhereClauseFromCriteria(predicate, queryState);
        }

        buildProjection(query.selection(), group, countObj);

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

        boolean hasAggregation = !group.isEmpty() || !countObj.isEmpty();
        boolean needsPipeline = hasAggregation || (!sortObj.isEmpty() && !predicateObj.isEmpty());
        if (needsPipeline) {
            List<Map<String, Object>> pipeline = new ArrayList<>();
            if (!predicateObj.isEmpty()) {
                pipeline.add(Map.of("$match", predicateObj));
            }
            if (!group.isEmpty()) {
                group.put("_id", null);
                pipeline.add(Map.of("$group", group));
            }
            if (!countObj.isEmpty()) {
                pipeline.add(countObj);
            }
            if (!sortObj.isEmpty()) {
                pipeline.add(Map.of("$sort", sortObj));
            }
            if (query.offset() > 0) {
                pipeline.add(Map.of("$skip", query.offset()));
            }
            if (query.limit() != -1) {
                pipeline.add(Map.of("$limit", query.limit()));
            }
            String queryString = NitritePredicateVisitor.toJsonString(pipeline);
            return QueryResult.of(queryString, Collections.emptyList(), queryState.getParameterBindings());
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
            queryString, Collections.emptyList(), queryState.getParameterBindings());
    }

    @Override
    public QueryResult buildUpdate(
        @NonNull final AnnotationMetadata annotationMetadata,
        @NonNull final UpdateQueryDefinition definition) {
        NitriteQueryState queryState = new NitriteQueryState(definition.persistentEntity());
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
                    setObj.put(fieldName, Map.of(QUERY_PARAMETER_PLACEHOLDER, index));
                } else {
                    setObj.put(fieldName, value);
                }
            }
        }

        String predicateString = predicateObj.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(predicateObj);
        String updateString = setObj.isEmpty() ? null : NitritePredicateVisitor.toJsonString(Collections.singletonMap("$set", setObj));
        List<QueryParameterBinding> parameterBindings = queryState.getParameterBindings();
        return new QueryResult() {
            @Override
            public String getQuery() { return predicateString; }
            @Override
            public String getUpdate() { return updateString; }
            @Override
            public List<String> getQueryParts() { return Collections.emptyList(); }
            @Override
            public List<QueryParameterBinding> getParameterBindings() { return parameterBindings; }
        };
    }

    @Override
    public QueryResult buildDelete(
        @NonNull final AnnotationMetadata annotationMetadata,
        @NonNull final DeleteQueryDefinition definition) {
        NitriteQueryState queryState = new NitriteQueryState(definition.persistentEntity());
        Predicate predicate = definition.predicate();
        Map<String, Object> predicateObj = new LinkedHashMap<>();
        if (predicate != null) {
            predicateObj = buildWhereClauseFromCriteria(predicate, queryState);
        }
        String queryString =
            predicateObj.isEmpty() ? "{}" : NitritePredicateVisitor.toJsonString(predicateObj);
        return QueryResult.of(
            queryString,
            Collections.emptyList(),
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

    private void buildProjection(Selection<?> selection, Map<String, Object> group, Map<String, Object> countObj) {
        switch (selection) {
            case UnaryExpression<?> unary -> {
                switch (unary.getType()) {
                    case SUM, AVG, MAX, MIN -> {
                        PersistentPropertyPath propertyPath = CriteriaUtils.requireProperty(unary.getExpression()).getPropertyPath();
                        String op = switch (unary.getType()) {
                            case SUM -> "$sum";
                            case AVG -> "$avg";
                            case MAX -> "$max";
                            case MIN -> "$min";
                            default ->
                                throw new IllegalStateException("Unexpected: " + unary.getType());
                        };
                        group.put(propertyPath.getProperty().getName(), Map.of(op, "$" + propertyPath.getPath()));
                    }
                    case COUNT, COUNT_DISTINCT -> countObj.put("$count", "result");
                    default -> { /* ignore */ }
                }
            }
            case CompoundSelection<?> compound -> {
                for (Selection<?> item : compound.getCompoundSelectionItems()) {
                    buildProjection(item, group, countObj);
                }
            }
            case null, default -> {
            }
        }
    }
}
