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
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledNitriteFilter;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledValue;
import io.micronaut.data.nitrite.runtime.query.ast.NitriteFilterAST;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.ALL;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.AND;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.BETWEEN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.CONCAT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.DIVIDE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EMPTY;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EQ;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EXPR;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.GT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.GTE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.IN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.INTERSECTS;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LIKE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LTE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.MULTIPLY;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NEAR;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NIN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NOT_NULL;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NULL;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.OR;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.REGEX;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.RIGHT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.STR_LEN_CP;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.SUBSTR_CP;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.TEXT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.TO_DOUBLE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.TO_LOWER;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.TO_UPPER;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.WITHIN;

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
            if (AND.equals(key)) {
                if (value instanceof List<?> list) {
                    List<NitriteFilterAST> ands = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            ands.add((NitriteFilterAST) compile(entity, toStringObjectMap(m)));
                        }
                    }
                    compiledFilters.add(new NitriteFilterAST.AndNode(ands));
                }
            } else if (OR.equals(key)) {
                if (value instanceof List<?> list) {
                    List<NitriteFilterAST> ors = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            ors.add((NitriteFilterAST) compile(entity, toStringObjectMap(m)));
                        }
                    }
                    compiledFilters.add(new NitriteFilterAST.OrNode(ors));
                }
            } else if (EXPR.equals(key)) {
                if (value instanceof Map<?, ?> m && m.size() == 1) {
                    Map.Entry<?, ?> exprEntry = m.entrySet().iterator().next();
                    String op = (String) exprEntry.getKey();
                    if (exprEntry.getValue() instanceof List<?> operands && operands.size() == 2) {
                        compiledFilters.add(new NitriteFilterAST.ExprNode(
                            op, compileExprValue(entity, operands.get(0)), compileExprValue(entity, operands.get(1))));
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
     * a computed operator, or a literal/bound-parameter value.
     */
    @SuppressWarnings("unchecked")
    private NitriteFilterAST.ExprValueNode compileExprValue(RuntimePersistentEntity<?> entity, Object node) {
        if (node instanceof String s && s.startsWith("$") && !isPlaceholder(s)) {
            return new NitriteFilterAST.ExprValueNode.FieldRef(normalizeExprField(entity, s.substring(1)));
        }
        if (node instanceof List<?> list) {
            List<NitriteFilterAST.ExprValueNode> compiled = new ArrayList<>(list.size());
            for (Object item : list) {
                compiled.add(compileExprValue(entity, item));
            }
            return new NitriteFilterAST.ExprValueNode.ListValue(compiled);
        }
        if (node instanceof Map<?, ?> m && m.size() == 1) {
            Map.Entry<?, ?> entry = m.entrySet().iterator().next();
            if (STR_LEN_CP.equals(entry.getKey())) {
                return new NitriteFilterAST.ExprValueNode.StrLen(compileExprValue(entity, entry.getValue()));
            }
            if (TO_LOWER.equals(entry.getKey())) {
                return new NitriteFilterAST.ExprValueNode.ToLower(compileExprValue(entity, entry.getValue()));
            }
            if (TO_UPPER.equals(entry.getKey())) {
                return new NitriteFilterAST.ExprValueNode.ToUpper(compileExprValue(entity, entry.getValue()));
            }
            if (MULTIPLY.equals(entry.getKey()) && entry.getValue() instanceof List<?> operands) {
                List<NitriteFilterAST.ExprValueNode> compiled = new ArrayList<>(operands.size());
                for (Object operand : operands) {
                    compiled.add(compileExprValue(entity, operand));
                }
                return new NitriteFilterAST.ExprValueNode.Multiply(compiled);
            }
            if (CONCAT.equals(entry.getKey()) && entry.getValue() instanceof List<?> operands) {
                List<NitriteFilterAST.ExprValueNode> compiled = new ArrayList<>(operands.size());
                for (Object operand : operands) {
                    compiled.add(compileExprValue(entity, operand));
                }
                return new NitriteFilterAST.ExprValueNode.Concat(compiled);
            }
            if (SUBSTR_CP.equals(entry.getKey()) && entry.getValue() instanceof List<?> operands && operands.size() == 3) {
                return new NitriteFilterAST.ExprValueNode.Substr(
                    compileExprValue(entity, operands.get(0)),
                    compileExprValue(entity, operands.get(1)),
                    compileExprValue(entity, operands.get(2)));
            }
            if (RIGHT.equals(entry.getKey()) && entry.getValue() instanceof List<?> operands && operands.size() == 2) {
                return new NitriteFilterAST.ExprValueNode.Right(
                    compileExprValue(entity, operands.get(0)),
                    compileExprValue(entity, operands.get(1)));
            }
            if (DIVIDE.equals(entry.getKey()) && entry.getValue() instanceof List<?> operands && operands.size() == 2) {
                return new NitriteFilterAST.ExprValueNode.Divide(
                    compileExprValue(entity, operands.get(0)),
                    compileExprValue(entity, operands.get(1)));
            }
            if (TO_DOUBLE.equals(entry.getKey())) {
                return new NitriteFilterAST.ExprValueNode.ToDouble(compileExprValue(entity, entry.getValue()));
            }
        }
        return new NitriteFilterAST.ExprValueNode.Literal(valueResolver.compileValue(node));
    }

    private String normalizeExprField(RuntimePersistentEntity<?> entity, String field) {
        PathResolver.PathResolution resolution = PathResolver.resolve(entity, field);
        if (!resolution.isReference()) {
            return entityMapper.normalizeFieldName(field, entity);
        }
        return resolution.persistedField();
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
                        || identity.getPersistedName().equals(rawField)
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
            operators = Collections.singletonMap(EQ, rawValue);
            operatorValues.put(EQ, valueResolver.compileValue(rawValue));
        }

        if (resolution.isReference() || rawField.contains(".")) {
            return new NitriteFilterAST.AssociationFieldNode(
                this::buildAssociationOrNestedField, entity, rawField, persistedName, operators);
        }
        if (!isOperatorMap) {
            CompiledValue eqValue = operatorValues.get(EQ);
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
            if (identity.getName().equals(rawField)
                    || identity.getPersistedName().equals(rawField)
                    || "id".equals(rawField)
                    || "_id".equals(rawField)) {
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
        r.put(EQ,  (e, f, v, p, n) -> entityMapper.eqWithNumericCoercion(e, f, v, f));
        r.put(NE,  (e, f, v, p, n) -> FluentFilter.where(f).notEq(v));
        r.put(GT,  (e, f, v, p, n) -> buildRangeFilter(f, GT, v));
        r.put(GTE, (e, f, v, p, n) -> buildRangeFilter(f, GTE, v));
        r.put(LT,  (e, f, v, p, n) -> buildRangeFilter(f, LT, v));
        r.put(LTE, (e, f, v, p, n) -> buildRangeFilter(f, LTE, v));
        r.put(IN,  (e, f, v, p, n) -> buildInFilter(e, f, v, p, n));
        r.put(NIN, (e, f, v, p, n) -> buildNotInFilter(e, f, v, p, n));
        r.put(NULL,    (e, f, v, p, n) -> Boolean.TRUE.equals(v) ? FluentFilter.where(f).eq(null) : Filter.ALL);
        r.put(NOT_NULL, (e, f, v, p, n) -> Boolean.TRUE.equals(v) ? FluentFilter.where(f).notEq(null) : Filter.ALL);
        r.put(BETWEEN, (e, f, v, p, n) -> {
            if (v instanceof List<?> list && list.size() == 2) {
                Object v1 = entityMapper.toFilterValue(valueResolver.preConvertForFilter(valueResolver.resolveValue(list.get(0), p, n)));
                Object v2 = entityMapper.toFilterValue(valueResolver.preConvertForFilter(valueResolver.resolveValue(list.get(1), p, n)));
                return buildBetweenFilter(f, v1, v2);
            }
            return Filter.ALL;
        });
        r.put(REGEX, (e, f, v, p, n) -> FluentFilter.where(f).regex(PatternConverter.resolveRegexPattern(valueResolver.resolveValue(v, p, n))));
        r.put(LIKE,  (e, f, v, p, n) -> {
            Object resolved = valueResolver.resolveValue(v, p, n);
            return FluentFilter.where(f).regex(resolved != null ? PatternConverter.convertLikeToRegex(resolved.toString()) : "");
        });
        r.put(NitriteQueryOperators.NOT, (e, f, v, p, n) -> {
            if (v instanceof Map<?, ?> m) {
                Filter filter = buildFieldFilter(e, f, toStringObjectMap(m), p, n);
                return filter != null ? filter.not() : Filter.ALL;
            }
            return Filter.ALL;
        });
        r.put(NitriteQueryOperators.EXISTS, (e, f, v, p, n) -> Boolean.TRUE.equals(v)
            ? FluentFilter.where(f).notEq(null) : FluentFilter.where(f).eq(null));
        r.put(EMPTY, (e, f, v, p, n) -> Boolean.TRUE.equals(v)
            ? Filter.or(FluentFilter.where(f).eq(""), FluentFilter.where(f).eq(null))
            : Filter.and(FluentFilter.where(f).notEq(""), FluentFilter.where(f).notEq(null)));
        r.put(TEXT,       (e, f, v, p, n) -> FluentFilter.where(f).text(v != null ? v.toString() : ""));
        r.put(ALL, (e, f, v, p, n) -> buildArrayContainsFilter(f, v, p, n));
        r.put(NEAR,       (e, f, v, p, n) -> spatialFactory.buildNearFilter(f, v, p, n));
        r.put(WITHIN,     (e, f, v, p, n) -> spatialFactory.createSpatialFilter(f, v, "within"));
        r.put(INTERSECTS, (e, f, v, p, n) -> spatialFactory.createSpatialFilter(f, v, "intersects"));
        return Collections.unmodifiableMap(r);
    }

    private Filter buildInFilter(RuntimePersistentEntity<?> entity, String field, @Nullable Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        if (finalValue == null) {
            return NONE;
        }
        List<Comparable<?>> values = coerceCollectionValues(entity, field, valueResolver.resolveCollection(finalValue, params, namedParameters));
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

    private Filter buildNotInFilter(RuntimePersistentEntity<?> entity, String field, @Nullable Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        if (finalValue == null) {
            return Filter.ALL;
        }
        List<Comparable<?>> values = coerceCollectionValues(entity, field, valueResolver.resolveCollection(finalValue, params, namedParameters));
        return values.isEmpty() ? Filter.ALL : FluentFilter.where(field).notIn(values.toArray(new Comparable[0]));
    }

    private List<Comparable<?>> coerceCollectionValues(RuntimePersistentEntity<?> entity, String field, List<Comparable<?>> values) {
        if (values.isEmpty()) {
            return values;
        }
        RuntimePersistentProperty<?> property = findProperty(entity, field);
        if (property == null || (property.getType() != char.class && property.getType() != Character.class)) {
            return values;
        }
        List<Comparable<?>> coerced = new ArrayList<>(values.size());
        for (Comparable<?> value : values) {
            if (value instanceof String string && string.length() == 1) {
                coerced.add(string.charAt(0));
            } else {
                coerced.add(value);
            }
        }
        return coerced;
    }

    private @Nullable RuntimePersistentProperty<?> findProperty(@Nullable RuntimePersistentEntity<?> entity, String field) {
        if (entity == null) {
            return null;
        }
        for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
            if (property.getName().equals(field) || property.getPersistedName().equals(field)) {
                return property;
            }
        }
        try {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            if (identity != null && (identity.getName().equals(field) || identity.getPersistedName().equals(field) || "_id".equals(field))) {
                return identity;
            }
        } catch (IllegalStateException ignored) {
            // Entity has no identity.
        }
        return null;
    }

    private Filter buildRangeFilter(String field, String op, @Nullable Object value) {
        if (!(value instanceof Comparable<?> comparable)) {
            return Filter.ALL;
        }
        return pair -> compareValues(pair.getSecond().get(field), comparable, op);
    }

    private Filter buildBetweenFilter(String field, @Nullable Object lower, @Nullable Object upper) {
        if (!(lower instanceof Comparable<?> lowerComparable) || !(upper instanceof Comparable<?> upperComparable)) {
            return Filter.ALL;
        }
        return pair -> compareValues(pair.getSecond().get(field), lowerComparable, GTE)
            && compareValues(pair.getSecond().get(field), upperComparable, LTE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean compareValues(@Nullable Object left, Object right, String op) {
        if (left == null || right == null) {
            return false;
        }
        int comparison;
        if (left instanceof Number || right instanceof Number) {
            BigDecimal leftNumber = toBigDecimal(left);
            BigDecimal rightNumber = toBigDecimal(right);
            if (leftNumber == null || rightNumber == null) {
                return false;
            }
            comparison = leftNumber.compareTo(rightNumber);
        } else if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            comparison = comparable.compareTo(right);
        } else {
            return false;
        }
        return switch (op) {
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
            default -> false;
        };
    }

    private static @Nullable BigDecimal toBigDecimal(Object value) {
        try {
            return switch (value) {
                case BigDecimal bigDecimal -> bigDecimal;
                case Number number -> new BigDecimal(number.toString());
                case String string -> new BigDecimal(string);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
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
