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
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.nitrite.model.query.builder.compile.CompileExpressionHandler;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds Nitrite JSON filter queries from Micronaut Data criteria expressions.
 * Generates JSON that is interpreted at runtime by {@link io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder}.
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
    private final AnnotationMetadata queryBuilderMetadata;

    /**
     * Creates a new NitriteQueryBuilder.
     */
    public NitriteQueryBuilder() {
        this(AnnotationMetadata.EMPTY_METADATA);
    }

    /**
     * Creates a new NitriteQueryBuilder with annotation metadata.
     *
     * @param annotationMetadata the annotation metadata
     */
    public NitriteQueryBuilder(AnnotationMetadata annotationMetadata) {
        this.queryBuilderMetadata = annotationMetadata;
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
        LOG.debug("buildSelect: entity={}, predicate={}", query.persistentEntity().getName(), query.predicate());
        NitriteQueryState queryState = new NitriteQueryState(query.persistentEntity());
        List<Map<String, Object>> lookupPipeline = new ArrayList<>();
        NitriteQueryBuilderHelper.addLookups(query.getJoinPaths(), query.persistentEntity(), lookupPipeline);

        Map<String, Object> predicateObj = new LinkedHashMap<>();
        Map<String, Object> group = new LinkedHashMap<>();
        Map<String, Object> countObj = new LinkedHashMap<>();
        Map<String, Object> sortObj = new LinkedHashMap<>();

        Predicate predicate = query.predicate();
        if (predicate != null) {
            predicateObj = buildWhereClauseFromCriteria(predicate, queryState);
        }

        NitriteQueryBuilderHelper.buildProjection(query.selection(), group, countObj);

        List<Order> orders = query.order();
        if (!orders.isEmpty()) {
            orders.forEach(
                order -> {
                    io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath =
                        CriteriaUtils.requireProperty(order.getExpression());
                    String fieldName = NitriteFieldNameResolver.getFieldName(propertyPath.getPropertyPath());
                    sortObj.put(fieldName, order.isAscending() ? 1 : -1);
                });
        }

        boolean hasLookups = !lookupPipeline.isEmpty();
        boolean hasAggregation = !group.isEmpty() || !countObj.isEmpty();
        boolean needsPipeline = hasLookups || hasAggregation || (!sortObj.isEmpty() && !predicateObj.isEmpty());
        if (needsPipeline) {
            List<Map<String, Object>> pipeline = new ArrayList<>(lookupPipeline);
            if (!predicateObj.isEmpty()) {
                pipeline.add(Map.of("$match", predicateObj));
            }
            if (!group.isEmpty()) {
                group.putIfAbsent("_id", null);
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
            String queryString = NitriteQuerySerializer.toJsonString(pipeline);
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

        String queryString = topLevel.isEmpty() ? "{}" : NitriteQuerySerializer.toJsonString(topLevel);
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

        String predicateString = predicateObj.isEmpty() ? "{}" : NitriteQuerySerializer.toJsonString(predicateObj);
        String updateString = setObj.isEmpty() ? null : NitriteQuerySerializer.toJsonString(Collections.singletonMap("$set", setObj));
        List<QueryParameterBinding> parameterBindings = queryState.getParameterBindings();
        return new QueryResult() {
            @Override
            public String getQuery() {
                return predicateString;
            }

            @Override
            public String getUpdate() {
                return updateString;
            }

            @Override
            public List<String> getQueryParts() {
                return Collections.emptyList();
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }
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
            predicateObj.isEmpty() ? "{}" : NitriteQuerySerializer.toJsonString(predicateObj);
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
        return obj.isEmpty() ? "{}" : NitriteQuerySerializer.toJsonString(obj);
    }

    private Map<String, Object> buildWhereClauseFromCriteria(
        final Predicate predicate, final NitriteQueryState queryState) {
        if (predicate == null) {
            return Map.of();
        }
        Map<String, Object> queryMap = new LinkedHashMap<>();
        if (predicate instanceof IPredicate predicateVisitable) {
            NitriteExpressionHandler handler = queryBuilderMetadata != AnnotationMetadata.EMPTY_METADATA
                ? new CompileExpressionHandler()
                : new RuntimeExpressionHandler();
            predicateVisitable.visitPredicate(new NitritePredicateVisitor(queryState, queryMap, handler));
        } else {
            throw new IllegalStateException(
                "Unsupported predicate type: " + predicate.getClass().getName());
        }
        return queryMap;
    }
}
