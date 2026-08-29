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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.nitrite.model.query.NitriteInternalKeys;
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;
import io.micronaut.data.nitrite.model.query.builder.compile.CompileExpressionHandler;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds Nitrite JSON filter queries from Micronaut Data criteria expressions.
 * Generates JSON that is interpreted at runtime by {@link io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder}.
 *
 * <p><strong>Note on explicitly defined queries:</strong> This builder and the underlying Nitrite engine
 * explicitly do <strong>not</strong> support SQL strings. The legacy SQL-parsing support present in
 * older versions (4.14.x) has been entirely removed. Any explicitly defined {@code @Query} must use
 * Nitrite's JSON filter syntax.
 *
 * @since 5.2.0
 */
@Internal
@Introspected
@TypeHint(NitriteQueryBuilder.class)
public final class NitriteQueryBuilder implements QueryBuilder {

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
        return QueryResult.of("", List.of(), List.of(), Map.of());
    }

    @Override
    public QueryResult buildSelect(
        @NonNull final AnnotationMetadata annotationMetadata,
        @NonNull final SelectQueryDefinition query) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("buildSelect: entity={}, predicate={}", query.persistentEntity().getName(), query.predicate());
        }
        NitriteQueryState queryState = new NitriteQueryState(query.persistentEntity());
        List<Map<String, Object>> lookupPipeline = new ArrayList<>();
        NitriteQueryBuilderHelper.addLookups(query.getJoinPaths(), query.persistentEntity(), lookupPipeline);

        Map<String, Object> predicateObj = new LinkedHashMap<>();
        Map<String, Object> group = new LinkedHashMap<>();
        Map<String, Object> countObj = new LinkedHashMap<>();
        Map<String, Object> projectionObj = new LinkedHashMap<>();
        Map<String, Object> sortObj = new LinkedHashMap<>();

        Predicate predicate = query.predicate();
        if (predicate != null) {
            predicateObj = buildWhereClauseFromCriteria(predicate, queryState);
        }

        NitriteQueryBuilderHelper.buildProjection(query.selection(), group, projectionObj, countObj);

        List<Order> orders = query.order();
        if (!orders.isEmpty()) {
            orders.forEach(
                order -> {
                    PersistentPropertyPath<?> propertyPath =
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
                pipeline.add(Map.of(NitriteQueryOperators.MATCH, predicateObj));
            }
            if (!group.isEmpty()) {
                group.putIfAbsent("_id", null);
                pipeline.add(Map.of(NitriteQueryOperators.GROUP, group));
            }
            if (!countObj.isEmpty()) {
                pipeline.add(countObj);
            }
            if (!sortObj.isEmpty()) {
                pipeline.add(Map.of(NitriteQueryOperators.SORT, sortObj));
            }
            if (!projectionObj.isEmpty()) {
                pipeline.add(Map.of(NitriteQueryOperators.PROJECT, projectionObj));
            }
            if (query.offset() > 0) {
                pipeline.add(Map.of(NitriteQueryOperators.SKIP, query.offset()));
            }
            if (query.limit() != -1) {
                pipeline.add(Map.of(NitriteQueryOperators.LIMIT, query.limit()));
            }
            String queryString = NitriteQuerySerializer.toJsonString(pipeline);
            return QueryResult.of(
                queryString,
                List.of(),
                queryState.getParameterBindings(),
                Map.of(),
                query.limit(),
                query.offset(),
                query.getJoinPaths());
        }

        Map<String, Object> topLevel = new LinkedHashMap<>();
        if (!predicateObj.isEmpty()) {
            topLevel.putAll(predicateObj);
        }
        if (!sortObj.isEmpty()) {
            topLevel.put(NitriteQueryOperators.SORT, sortObj);
        }
        if (!projectionObj.isEmpty()) {
            topLevel.put(NitriteQueryOperators.PROJECT, projectionObj);
        }
        if (query.offset() > 0) {
            topLevel.put(NitriteQueryOperators.SKIP, query.offset());
        }
        if (query.limit() != -1) {
            topLevel.put(NitriteQueryOperators.LIMIT, query.limit());
        }

        String queryString = topLevel.isEmpty() ? "{}" : NitriteQuerySerializer.toJsonString(topLevel);
        return QueryResult.of(
            queryString,
            List.of(),
            queryState.getParameterBindings(),
            Map.of(),
            query.limit(),
            query.offset(),
            query.getJoinPaths());
    }

    /**
     * Builds the runtime filter directly from a select definition, skipping the JSON
     * serialize/reparse round trip {@link #buildSelect} needs to satisfy the shared
     * {@link QueryResult} contract (a compiled query must survive into generated bytecode as a
     * {@code String}; the runtime Criteria path has no such requirement). Mirrors the
     * predicate/projection/order assembly in {@link #buildSelect}.
     *
     * @param query the select definition
     * @return the runtime filter, or {@code null} if the query needs the aggregation pipeline
     *         (joins, group, count) — callers should fall back to {@link #buildSelect} for that case
     */
    @Nullable
    public NitriteRuntimeFilter buildRuntimeFilter(@NonNull final SelectQueryDefinition query) {
        NitriteQueryState queryState = new NitriteQueryState(query.persistentEntity());
        List<Map<String, Object>> lookupPipeline = new ArrayList<>();
        NitriteQueryBuilderHelper.addLookups(query.getJoinPaths(), query.persistentEntity(), lookupPipeline);

        Map<String, Object> predicateObj = new LinkedHashMap<>();
        Map<String, Object> group = new LinkedHashMap<>();
        Map<String, Object> countObj = new LinkedHashMap<>();
        Map<String, Object> projectionObj = new LinkedHashMap<>();
        Map<String, Object> sortObj = new LinkedHashMap<>();

        Predicate predicate = query.predicate();
        if (predicate != null) {
            predicateObj = buildWhereClauseFromCriteria(predicate, queryState);
        }

        NitriteQueryBuilderHelper.buildProjection(query.selection(), group, projectionObj, countObj);

        List<Order> orders = query.order();
        if (!orders.isEmpty()) {
            orders.forEach(
                order -> {
                    PersistentPropertyPath<?> propertyPath =
                        CriteriaUtils.requireProperty(order.getExpression());
                    String fieldName = NitriteFieldNameResolver.getFieldName(propertyPath.getPropertyPath());
                    sortObj.put(fieldName, order.isAscending() ? 1 : -1);
                });
        }

        boolean hasLookups = !lookupPipeline.isEmpty();
        boolean hasAggregation = !group.isEmpty() || !countObj.isEmpty();
        if (hasLookups || hasAggregation) {
            return null;
        }
        return new NitriteRuntimeFilter(predicateObj, sortObj, projectionObj, query.offset(), query.limit(), queryState.getParameterBindings());
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
        Map<String, Object> incObj = new LinkedHashMap<>();
        Map<String, Object> mulObj = new LinkedHashMap<>();
        Map<String, Object> concatObj = new LinkedHashMap<>();
        Map<String, Object> propertiesToUpdate = definition.propertiesToUpdate();
        if (propertiesToUpdate != null) {
            for (Map.Entry<String, Object> entry : propertiesToUpdate.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                PersistentProperty property = definition.persistentEntity().getPropertyByName(fieldName);
                String persistedName = property != null ? property.getPersistedName() : fieldName;
                if (value instanceof BinaryExpression<?> binaryExpression) {
                    PersistentPropertyPath<?> leftProperty = CriteriaUtils.requireProperty(binaryExpression.getLeft());
                    if (property != null && !leftProperty.getProperty().getName().equals(property.getName())) {
                        throw new IllegalStateException("Left property path does not match property path");
                    }
                    Object updateValue = Objects.requireNonNull(
                        updateExpressionValue(queryState, property, binaryExpression.getRight()),
                        () -> "Null operand in arithmetic update of property: " + persistedName);
                    switch (binaryExpression.getType()) {
                        case CONCAT -> concatObj.put(persistedName, updateValue);
                        case SUM -> incObj.put(persistedName, updateValue);
                        case PROD -> mulObj.put(persistedName, updateValue);
                        case QUOT -> mulObj.put(persistedName, withFlag(updateValue, NitriteInternalKeys.RECIPROCATE));
                        case DIFF -> incObj.put(persistedName, withFlag(updateValue, NitriteInternalKeys.NEGATE));
                        default -> throw new IllegalStateException("Unsupported binary expression type: " + binaryExpression.getType());
                    }
                } else {
                    setObj.put(persistedName, updateExpressionValue(queryState, property, value));
                }
            }
        }

        String predicateString = predicateObj.isEmpty() ? "{}" : NitriteQuerySerializer.toJsonString(predicateObj);
        Map<String, Object> updateObj = new LinkedHashMap<>();
        if (!setObj.isEmpty()) {
            updateObj.put(NitriteQueryOperators.SET, setObj);
        }
        if (!incObj.isEmpty()) {
            updateObj.put(NitriteQueryOperators.INC, incObj);
        }
        if (!mulObj.isEmpty()) {
            updateObj.put(NitriteQueryOperators.MUL, mulObj);
        }
        if (!concatObj.isEmpty()) {
            updateObj.put(NitriteQueryOperators.CONCAT, concatObj);
        }
        String updateString = updateObj.isEmpty() ? null : NitriteQuerySerializer.toJsonString(updateObj);
        List<QueryParameterBinding> parameterBindings = queryState.getParameterBindings();
        return new QueryResult() {
            @Override
            public String getQuery() {
                return predicateString;
            }

            @Override
            public @Nullable String getUpdate() {
                return updateString;
            }

            @Override
            public List<String> getQueryParts() {
                return List.of();
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }
        };
    }

    /**
     * Resolves the value written for one updated property: a bound parameter becomes a placeholder
     * the runtime resolves later, a literal is unwrapped to the value it carries, and anything else
     * is written as-is.
     *
     * @param queryState the query state collecting parameter bindings
     * @param property the property being updated, {@code null} when it is not mapped
     * @param value the raw value from the update definition
     * @return the value to write into the update document
     */
    private @Nullable Object updateExpressionValue(NitriteQueryState queryState, @Nullable PersistentProperty property, Object value) {
        if (value instanceof BindingParameter bindingParameter) {
            int index = queryState.pushParameter(bindingParameter, NitritePredicateVisitor.newBindingContext(property));
            return Map.of(NitriteInternalKeys.QUERY_PARAMETER_PLACEHOLDER, index);
        }
        if (value instanceof LiteralExpression<?> literalExpression) {
            return literalExpression.getValue();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Object withFlag(Object value, String flag) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> flagged = new LinkedHashMap<>((Map<String, Object>) map);
            flagged.put(flag, true);
            return flagged;
        }
        return Map.of(NitriteQueryOperators.VALUE, value, flag, true);
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
            List.of(),
            queryState.getParameterBindings());
    }

    @Override
    @NonNull
    public String buildLimitAndOffset(final long limit, final long offset) {
        Map<String, Object> obj = new LinkedHashMap<>();
        if (offset > 0) {
            obj.put(NitriteQueryOperators.SKIP, (int) offset);
        }
        if (limit > 0) {
            obj.put(NitriteQueryOperators.LIMIT, (int) limit);
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
            NitriteExpressionHandler handler = !queryBuilderMetadata.equals(AnnotationMetadata.EMPTY_METADATA)
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
