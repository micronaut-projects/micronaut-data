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
package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledNitriteFilter;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledValue;
import io.micronaut.data.nitrite.runtime.query.ast.NitriteFilterAST;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builder for Nitrite Filters from JSON-like structures.
 * <p>
 * Supports association path resolution for {@code ONE_TO_MANY} and {@code MANY_TO_MANY} relationships.
 * Association names are matched using the associated entity's class name, which correctly handles
 * both regular and irregular plural forms (for example, {@code books} → {@code book}, {@code cities} → {@code city}).
 *
 * @since 1.0.0
 */
@Internal
public final class NitriteFilterBuilder {

    private static final Filter NONE = element -> false;

    private final NitriteEntityMapper entityMapper;
    private final ValueResolver valueResolver;
    private final SpatialFilterFactory spatialFactory;
    private final AssociationFilterResolver assocResolver;
    private final Map<String, OperatorHandler> operatorRegistry;

    /**
     * Create a new filter builder.
     *
     * @param entityMapper the entity mapper
     */
    public NitriteFilterBuilder(NitriteEntityMapper entityMapper) {
        this(entityMapper, null);
    }

    /**
     * Create a new filter builder with sub-query support.
     *
     * @param entityMapper the entity mapper
     * @param subQueryExecutor the sub-query executor for auto-join on associations
     */
    public NitriteFilterBuilder(NitriteEntityMapper entityMapper, @Nullable SubQueryExecutor subQueryExecutor) {
        this.entityMapper = entityMapper;
        this.valueResolver = new ValueResolver(entityMapper);
        this.spatialFactory = new SpatialFilterFactory(entityMapper, valueResolver);
        this.assocResolver = new AssociationFilterResolver(
            subQueryExecutor, valueResolver,
            this::buildFieldFilter,
            this::buildOperatorFiltersForPath);
        this.operatorRegistry = buildOperatorRegistry();
    }

    /**
     * Compile a filter map into a reusable CompiledNitriteFilter.
     *
     * @param entity    the entity metadata
     * @param filterObj the filter object map
     * @return the compiled filter
     */
    public CompiledNitriteFilter compile(final RuntimePersistentEntity<?> entity, final Map<String, Object> filterObj) {
        if (filterObj == null || filterObj.isEmpty()) {
            return new NitriteFilterAST.AllNode();
        }

        final List<NitriteFilterAST> compiledFilters = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filterObj.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key != null && (key.equals("$sort") || key.equals("$set") || key.equals("$limit")
                    || key.equals("$skip") || key.equals("$count") || key.equals("$project"))) {
                continue;
            }
            if ("$and".equals(key)) {
                if (value instanceof List<?> list) {
                    List<NitriteFilterAST> ands = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            ands.add((NitriteFilterAST) compile(entity, toStringObjectMap(m)));
                        }
                    }
                    compiledFilters.add(new NitriteFilterAST.AndNode(ands));
                }
            } else if ("$or".equals(key)) {
                if (value instanceof List<?> list) {
                    List<NitriteFilterAST> ors = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            ors.add((NitriteFilterAST) compile(entity, toStringObjectMap(m)));
                        }
                    }
                    compiledFilters.add(new NitriteFilterAST.OrNode(ors));
                }
            } else if ("$expr".equals(key)) {
                if (value instanceof Map<?, ?> m && m.size() == 1) {
                    Map.Entry<?, ?> exprEntry = m.entrySet().iterator().next();
                    String op = (String) exprEntry.getKey();
                    if (exprEntry.getValue() instanceof List<?> operands && operands.size() == 2) {
                        compiledFilters.add(new NitriteFilterAST.ExprNode(
                            op, compileExprValue(operands.get(0)), compileExprValue(operands.get(1))));
                    }
                }
            } else {
                compiledFilters.add(compileFieldFilter(entity, key, value));
            }
        }

        if (compiledFilters.isEmpty()) {
            return new NitriteFilterAST.AllNode();
        }
        if (compiledFilters.size() == 1) {
            return compiledFilters.getFirst();
        }
        return new NitriteFilterAST.AndNode(compiledFilters);
    }

    /**
     * Compile a {@code $expr} value tree node: a field reference ({@code "$fieldName"}),
     * a {@code $strLenCP}/{@code $multiply} operator, or a literal/bound-parameter value.
     */
    @SuppressWarnings("unchecked")
    private NitriteFilterAST.ExprValueNode compileExprValue(Object node) {
        if (node instanceof String s && s.startsWith("$") && !isPlaceholder(s)) {
            return new NitriteFilterAST.ExprValueNode.FieldRef(s.substring(1));
        }
        if (node instanceof Map<?, ?> m && m.size() == 1) {
            Map.Entry<?, ?> entry = m.entrySet().iterator().next();
            if ("$strLenCP".equals(entry.getKey())) {
                return new NitriteFilterAST.ExprValueNode.StrLen(compileExprValue(entry.getValue()));
            }
            if ("$multiply".equals(entry.getKey()) && entry.getValue() instanceof List<?> operands) {
                List<NitriteFilterAST.ExprValueNode> compiled = new ArrayList<>(operands.size());
                for (Object operand : operands) {
                    compiled.add(compileExprValue(operand));
                }
                return new NitriteFilterAST.ExprValueNode.Multiply(compiled);
            }
        }
        return new NitriteFilterAST.ExprValueNode.Literal(valueResolver.compileValue(node));
    }

    private NitriteFilterAST compileFieldFilter(
            final RuntimePersistentEntity<?> entity,
            final String rawField,
            final Object rawValue) {

        PathResolver.PathResolution resolution = PathResolver.resolve(entity, rawField);
        String persistedName = resolution.persistedField();

        // Identity fields need the entity-mapper normalization (e.g. "id" → Nitrite's internal name).
        if (!resolution.isReference() && entity != null) {
            try {
                RuntimePersistentProperty<?> identity = entity.getIdentity();
                if (identity != null && (identity.getName().equals(rawField)
                        || "id".equals(rawField) || "_id".equals(rawField))) {
                    persistedName = entityMapper.normalizeFieldName(rawField, entity);
                }
            } catch (IllegalStateException ignored) {
                // Entity metadata may not expose an identity property for all mappings.
            }
        }

        final Map<String, Object> operators;
        final Map<String, CompiledValue> operatorValues = new LinkedHashMap<>();
        boolean isOperatorMap = false;
        if (rawValue instanceof Map<?, ?> m && !isPlaceholder(rawValue)) {
            isOperatorMap = true;
            operators = toStringObjectMap(m);
            for (Map.Entry<String, Object> entry : operators.entrySet()) {
                operatorValues.put(entry.getKey(), valueResolver.compileValue(entry.getValue()));
            }
        } else {
            operators = Collections.singletonMap("$eq", rawValue);
            operatorValues.put("$eq", valueResolver.compileValue(rawValue));
        }

        if (resolution.isReference() || rawField.contains(".")) {
            return new NitriteFilterAST.AssociationFieldNode(
                this::buildAssociationOrNestedField, entity, rawField, persistedName, operators);
        }
        if (!isOperatorMap) {
            CompiledValue eqValue = operatorValues.get("$eq");
            if (eqValue == null) {
                eqValue = new CompiledValue.Literal(null);
            }
            return new NitriteFilterAST.SimpleEqualityNode(this::prepareFilterValue, this::buildOperatorFilter, entity, persistedName, rawField, eqValue);
        }
        List<NitriteFilterAST.OperatorBinding> bindings = new ArrayList<>(operatorValues.size());
        for (Map.Entry<String, CompiledValue> entry : operatorValues.entrySet()) {
            bindings.add(new NitriteFilterAST.OperatorBinding(entry.getKey(), entry.getValue()));
        }
        return new NitriteFilterAST.SimpleOperatorNode(this::prepareFilterValue, this::buildOperatorFilter, entity, persistedName, rawField, bindings);
    }

    /**
     * Prepare a value for filtering.
     *
     * @param persistedName the persisted field name
     * @param value the raw value
     * @return the normalized value used for Nitrite filtering
     */
    public @Nullable Object prepareFilterValue(String persistedName, @Nullable Object value) {
        return entityMapper.toNitriteFilterValue(
            valueResolver.preConvertForFilter(valueResolver.maybeCoerceUuid(persistedName, value)));
    }

    /**
     * Build a Nitrite filter from a map structure.
     *
     * @param entity the entity metadata
     * @param filterObj the filter object
     * @param params positional parameters
     * @param namedParameters named parameters
     * @return the bound Nitrite filter
     */
    public Filter buildFilterFromJson(
            final RuntimePersistentEntity<?> entity,
            final Map<String, Object> filterObj,
            final Object[] params,
            final Map<String, Object> namedParameters) {
        return compile(entity, filterObj).bind(params, namedParameters);
    }

    /**
     * Build a filter for a field with operators.
     *
     * @param entity the entity metadata
     * @param rawField the raw field name
     * @param operators the operators to apply
     * @param params positional parameters
     * @param namedParameters named parameters
     * @return the Nitrite filter for the field
     */
    public @Nullable Filter buildFieldFilter(
            final RuntimePersistentEntity<?> entity,
            final String rawField,
            final Map<String, Object> operators,
            final Object[] params,
            final Map<String, Object> namedParameters) {

        String persistedName = rawField;
        if (entity != null) {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            if (identity.getName().equals(rawField) || "id".equals(rawField) || "_id".equals(rawField)) {
                persistedName = entityMapper.normalizeFieldName(rawField, entity);
            } else {
                RuntimePersistentProperty<?> prop = entity.getPropertyByName(rawField);
                if (prop != null) {
                    persistedName = prop.getPersistedName();
                }
            }
        }
        return buildAssociationOrNestedField(entity, rawField, persistedName, operators, params, namedParameters);
    }

    private @Nullable Filter buildAssociationOrNestedField(
            final RuntimePersistentEntity<?> entity,
            final String rawField,
            final String persistedName,
            final Map<String, Object> operators,
            final Object[] params,
            final Map<String, Object> namedParameters) {

        Filter assocFilter = assocResolver.buildAssociationFilter(entity, rawField, operators, params, namedParameters);
        if (assocFilter != null) {
            return assocFilter;
        }
        if (rawField.contains(".")) {
            return assocResolver.buildNestedFilter(entity, rawField, operators, params, namedParameters);
        }
        return buildOperatorFiltersForPath(entity, persistedName, operators, params, namedParameters);
    }

    /**
     * Builds a Nitrite filter for a specific operator and field on the given entity.
     * @param entity the runtime persistent entity being queried
     * @param field the persisted name of the field
     * @param op the operator string (e.g., "$eq", "$in")
     * @param finalValue the prepared value to compare against
     * @param params the positional parameters array
     * @param namedParameters the named parameters map
     * @return the constructed Nitrite Filter for the operator
     */
    public @Nullable Filter buildOperatorFilter(
            final RuntimePersistentEntity<?> entity,
            final String field,
            final String op,
            final @Nullable Object finalValue,
            final Object[] params,
            final Map<String, Object> namedParameters) {
        OperatorHandler handler = operatorRegistry.get(op);
        return handler != null
            ? handler.build(entity, field, finalValue, params, namedParameters)
            : FluentFilter.where(field).eq(finalValue);
    }

    private Map<String, OperatorHandler> buildOperatorRegistry() {
        Map<String, OperatorHandler> r = new LinkedHashMap<>();
        r.put("$eq",  (e, f, v, p, n) -> entityMapper.eqWithNumericCoercion(e, f, v, f));
        r.put("$ne",  (e, f, v, p, n) -> FluentFilter.where(f).notEq(v));
        r.put("$gt",  (e, f, v, p, n) -> v instanceof Comparable<?> c ? FluentFilter.where(f).gt(c) : Filter.ALL);
        r.put("$gte", (e, f, v, p, n) -> v instanceof Comparable<?> c ? FluentFilter.where(f).gte(c) : Filter.ALL);
        r.put("$lt",  (e, f, v, p, n) -> v instanceof Comparable<?> c ? FluentFilter.where(f).lt(c) : Filter.ALL);
        r.put("$lte", (e, f, v, p, n) -> v instanceof Comparable<?> c ? FluentFilter.where(f).lte(c) : Filter.ALL);
        r.put("$in",  (e, f, v, p, n) -> buildInFilter(f, v, p, n));
        r.put("$nin", (e, f, v, p, n) -> buildNotInFilter(f, v, p, n));
        r.put("$null",    (e, f, v, p, n) -> Boolean.TRUE.equals(v) ? FluentFilter.where(f).eq(null) : Filter.ALL);
        r.put("$notNull", (e, f, v, p, n) -> Boolean.TRUE.equals(v) ? FluentFilter.where(f).notEq(null) : Filter.ALL);
        r.put("$between", (e, f, v, p, n) -> {
            if (v instanceof List<?> list && list.size() == 2) {
                Object v1 = entityMapper.toFilterValue(valueResolver.preConvertForFilter(valueResolver.resolveValue(list.get(0), p, n)));
                Object v2 = entityMapper.toFilterValue(valueResolver.preConvertForFilter(valueResolver.resolveValue(list.get(1), p, n)));
                return FluentFilter.where(f).between((Comparable<?>) v1, (Comparable<?>) v2);
            }
            return Filter.ALL;
        });
        r.put("$regex", (e, f, v, p, n) -> FluentFilter.where(f).regex(PatternConverter.resolveRegexPattern(valueResolver.resolveValue(v, p, n))));
        r.put("$like",  (e, f, v, p, n) -> {
            Object resolved = valueResolver.resolveValue(v, p, n);
            return FluentFilter.where(f).regex(resolved != null ? PatternConverter.convertLikeToRegex(resolved.toString()) : "");
        });
        r.put("$not", (e, f, v, p, n) -> {
            if (v instanceof Map<?, ?> m) {
                Filter filter = buildFieldFilter(e, f, toStringObjectMap(m), p, n);
                return filter != null ? filter.not() : Filter.ALL;
            }
            return Filter.ALL;
        });
        r.put("$exists", (e, f, v, p, n) -> Boolean.TRUE.equals(v)
            ? FluentFilter.where(f).notEq(null) : FluentFilter.where(f).eq(null));
        r.put("$empty", (e, f, v, p, n) -> Boolean.TRUE.equals(v)
            ? Filter.or(FluentFilter.where(f).eq(""), FluentFilter.where(f).eq(null))
            : Filter.and(FluentFilter.where(f).notEq(""), FluentFilter.where(f).notEq(null)));
        r.put("$text",       (e, f, v, p, n) -> FluentFilter.where(f).text(v != null ? v.toString() : ""));
        r.put("$all", (e, f, v, p, n) -> buildArrayContainsFilter(f, v, p, n));
        r.put("$near",       (e, f, v, p, n) -> spatialFactory.buildNearFilter(f, v, p, n));
        r.put("$within",     (e, f, v, p, n) -> spatialFactory.createSpatialFilter(f, v, "within"));
        r.put("$intersects", (e, f, v, p, n) -> spatialFactory.createSpatialFilter(f, v, "intersects"));
        return Collections.unmodifiableMap(r);
    }

    private Filter buildInFilter(String field, @Nullable Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        if (finalValue == null) {
            return NONE;
        }
        List<Comparable<?>> values = valueResolver.resolveCollection(finalValue, params, namedParameters);
        return values.isEmpty() ? NONE : FluentFilter.where(field).in(values.toArray(new Comparable[0]));
    }

    private Filter buildArrayContainsFilter(String field, @Nullable Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        if (finalValue == null) {
            return Filter.ALL;
        }
        List<Comparable<?>> values = valueResolver.resolveCollection(finalValue, params, namedParameters);
        values = values.stream().filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return Filter.ALL;
        }
        if (values.size() == 1) {
            Comparable<?> first = values.getFirst();
            return first != null ? FluentFilter.where(field).elemMatch(FluentFilter.$.eq(first)) : Filter.ALL;
        }
        Filter[] filters = values.stream()
            .map(elem -> {
                if (elem == null) {
                    return Filter.ALL;
                }
                return (Filter) FluentFilter.where(field).elemMatch(FluentFilter.$.eq(elem));
            })
            .toArray(Filter[]::new);
        return Filter.and(filters);
    }

    private Filter buildNotInFilter(String field, @Nullable Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        if (finalValue == null) {
            return Filter.ALL;
        }
        List<Comparable<?>> values = valueResolver.resolveCollection(finalValue, params, namedParameters);
        return values.isEmpty() ? Filter.ALL : FluentFilter.where(field).notIn(values.toArray(new Comparable[0]));
    }

    private @Nullable Filter buildOperatorFiltersForPath(
            final RuntimePersistentEntity<?> entity,
            final String fullPath,
            final Map<String, Object> operators,
            final Object[] params,
            final Map<String, Object> namedParameters) {

        List<Filter> fieldFilters = new ArrayList<>();
        for (Map.Entry<String, Object> opEntry : operators.entrySet()) {
            String op = opEntry.getKey();
            Object value = valueResolver.resolveValue(opEntry.getValue(), params, namedParameters);
            Object finalValue = entityMapper.toNitriteFilterValue(
                valueResolver.preConvertForFilter(valueResolver.maybeCoerceUuid(fullPath, value)));
            Filter f = buildOperatorFilter(entity, fullPath, op, finalValue, params, namedParameters);
            if (f != null && !Filter.ALL.equals(f)) {
                fieldFilters.add(f);
            }
        }
        return fieldFilters.size() == 1 ? fieldFilters.getFirst() : Filter.and(fieldFilters.toArray(new Filter[0]));
    }

    private boolean isPlaceholder(Object value) {
        if (value instanceof String s && (s.startsWith("$mn_qp:") || s.startsWith(":"))) {
            return true;
        }
        return value instanceof Map<?, ?> vm && vm.size() == 1 && vm.containsKey("$mn_qp");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /**
     * Handler for a single filter operator (e.g. {@code $eq}, {@code $in}).
     * Registered at construction time; add new operators without touching the dispatch path.
     */
    @FunctionalInterface
    public interface OperatorHandler {
        /**
         * Handles the creation of a Nitrite filter for a specific comparison operator.
         * @param entity the runtime persistent entity being queried
         * @param field the persisted name of the field
         * @param value the value to compare against
         * @param params the positional parameters array
         * @param named the named parameters map
         * @return the constructed Nitrite Filter
         */
        @Nullable Filter build(RuntimePersistentEntity<?> entity, String field, @Nullable Object value, Object[] params, Map<String, Object> named);
    }

    /**
     * Functional interface for executing sub-queries on associated entities.
     */
    @FunctionalInterface
    public interface SubQueryExecutor {
        /**
         * Execute a sub-query on an associated entity and return matching values for a specific field.
         *
         * @param associatedEntity the associated entity metadata
         * @param filterMap the filter criteria
         * @param targetField the field to extract from matching documents (optional, defaults to identity if null)
         * @param params positional parameters
         * @param namedParameters named parameters
         * @return list of matching field values
         */
        List<Object> executeSubQuery(RuntimePersistentEntity<?> associatedEntity,
                                     Map<String, Object> filterMap,
                                     @Nullable String targetField,
                                     Object[] params,
                                     Map<String, Object> namedParameters);
    }
}
