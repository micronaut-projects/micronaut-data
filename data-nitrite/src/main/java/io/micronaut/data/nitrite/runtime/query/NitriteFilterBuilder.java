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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.compiled.CompiledNitriteFilter;
import io.micronaut.data.nitrite.runtime.query.compiled.CompiledValue;
import io.micronaut.data.nitrite.runtime.query.compiled.NitriteFilterAST;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static final Logger LOG = LoggerFactory.getLogger(NitriteFilterBuilder.class);
    
    // Spatial filter class names (optional dependencies)
    private static final String SPATIAL_FLUENT_FILTER_CLASS = "org.dizitart.no2.spatial.SpatialFluentFilter";
    private static final String GEO_POINT_CLASS = "org.dizitart.no2.spatial.GeoPoint";
    private static final String JTS_GEOMETRY_CLASS = "org.locationtech.jts.geom.Geometry";
    private static final String JTS_POINT_CLASS = "org.locationtech.jts.geom.Point";
    private static final String JTS_COORDINATE_CLASS = "org.locationtech.jts.geom.Coordinate";

    /**
     * A filter that matches no documents (used for empty IN clauses).
     */
    private static final Filter NONE = element -> false;

    private final NitriteEntityMapper entityMapper;
    private final SubQueryExecutor subQueryExecutor;

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
    public NitriteFilterBuilder(NitriteEntityMapper entityMapper, SubQueryExecutor subQueryExecutor) {
        this.entityMapper = entityMapper;
        this.subQueryExecutor = subQueryExecutor;
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
            if (key != null && (key.equals("$sort")
                || key.equals("$set")
                || key.equals("$limit")
                || key.equals("$skip")
                || key.equals("$count")
                || key.equals("$project"))) {
                continue;
            }

            if ("$and".equals(key)) {
                if (value instanceof List<?> list) {
                    final List<NitriteFilterAST> ands = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            ands.add((NitriteFilterAST) compile(entity, toStringObjectMap(m)));
                        }
                    }
                    compiledFilters.add(new NitriteFilterAST.AndNode(ands));
                }
            } else if ("$or".equals(key)) {
                if (value instanceof List<?> list) {
                    final List<NitriteFilterAST> ors = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            ors.add((NitriteFilterAST) compile(entity, toStringObjectMap(m)));
                        }
                    }
                    compiledFilters.add(new NitriteFilterAST.OrNode(ors));
                }
            } else {
                compiledFilters.add(compileFieldFilter(entity, key, value));
            }
        }

        if (compiledFilters.isEmpty()) {
            return new NitriteFilterAST.AllNode();
        }
        if (compiledFilters.size() == 1) {
            return compiledFilters.get(0);
        }
        return new NitriteFilterAST.AndNode(compiledFilters);
    }

    private NitriteFilterAST compileFieldFilter(
        final RuntimePersistentEntity<?> entity,
        final String rawField,
        final Object rawValue) {

        // Pre-normalize field name and find property metadata
        String persistedName = rawField;
        RuntimePersistentProperty<?> property = null;
        RuntimeAssociation<?> association = null;

        if (entity != null) {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            if (identity != null && (identity.getName().equals(rawField) || "id".equals(rawField) || "_id".equals(rawField))) {
                persistedName = entityMapper.normalizeFieldName(rawField, entity);
                property = identity;
            } else {
                property = entity.getPropertyByName(rawField);
                if (property != null) {
                    persistedName = property.getPersistedName();
                } else {
                    // Search by persisted name (handles FK fields like author_id)
                    for (RuntimePersistentProperty<?> p : entity.getPersistentProperties()) {
                        if (p.getPersistedName().equals(rawField)) {
                            property = p;
                            persistedName = rawField;
                            break;
                        }
                    }
                }
            }

            // Detect association potential
            if (property instanceof RuntimeAssociation<?> assoc) {
                association = assoc;
            } else if (property == null) {
                // Search for reverse lookup patterns
                for (RuntimePersistentProperty<?> p : entity.getPersistentProperties()) {
                    if (p instanceof RuntimeAssociation<?> assoc) {
                        Relation.Kind kind = assoc.getKind();
                        if (kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY) {
                            String pName = assoc.getPersistedName();
                            String sName = assoc.getAssociatedEntity().getSimpleName();
                            String dName = assoc.getAssociatedEntity().getDecapitalizedName();

                            if (rawField.contains(".")) {
                                String assocPart = rawField.substring(0, rawField.indexOf('.'));
                                if (assocPart.equals(pName) || assocPart.equals(sName) || assocPart.equals(dName)) {
                                    association = assoc;
                                    break;
                                }
                            } else if (rawField.startsWith(pName + "_") || rawField.startsWith(sName + "_") || rawField.startsWith(dName + "_") || rawField.equals(pName)) {
                                association = assoc;
                                break;
                            }
                        }
                    }
                }
            }
        }

        final Map<String, Object> operators;
        final Map<String, CompiledValue> operatorValues = new LinkedHashMap<>();
        boolean isOperatorMap = false;
        if (rawValue instanceof Map<?, ?> m && !isPlaceholder(rawValue)) {
            isOperatorMap = true;
            operators = toStringObjectMap(m);
            for (Map.Entry<String, Object> entry : operators.entrySet()) {
                operatorValues.put(entry.getKey(), compileValue(entry.getValue()));
            }
        } else {
            operators = Collections.singletonMap("$eq", rawValue);
            operatorValues.put("$eq", compileValue(rawValue));
        }

        // Strategy Decision:
        // 1. If it's a potential association or has dots, use the dynamic fallback (100% correct)
        if (association != null || rawField.contains(".")) {
            return new NitriteFilterAST.DynamicFieldNode(this, entity, rawField, operators);
        }

        // 2. Simple equality optimization (the most common case)
        if (!isOperatorMap) {
            return new NitriteFilterAST.SimpleEqualityNode(this, entity, persistedName, rawField, operatorValues.get("$eq"));
        }

        // 3. Simple operator optimization (gt, lt, in, etc.)
        List<NitriteFilterAST.OperatorBinding> bindings = new ArrayList<>(operatorValues.size());
        for (Map.Entry<String, CompiledValue> entry : operatorValues.entrySet()) {
            bindings.add(new NitriteFilterAST.OperatorBinding(entry.getKey(), entry.getValue()));
        }
        return new NitriteFilterAST.SimpleOperatorNode(this, entity, persistedName, rawField, bindings);
    }

    /**
     * Prepare a value for filtering (coercion, conversion, UUID handling).
     * @param persistedName The persisted field name
     * @param value The raw value
     * @param rawField The original field name
     * @return The prepared value
     */
    public Object prepareFilterValue(String persistedName, Object value, String rawField) {
        return entityMapper.toNitriteFilterValue(preConvertForFilter(maybeCoerceUuid(persistedName, value)), rawField);
    }

    private CompiledValue compileValue(final Object value) {
        if (value instanceof String s) {
            if (s.startsWith("$mn_qp:") && s.indexOf("$mn_qp:", 7) < 0) {
                try {
                    return new CompiledValue.Parameter(Integer.parseInt(s.substring(7)));
                } catch (Exception ignored) {
                }
            }
            if (s.startsWith(":")) {
                return new CompiledValue.NamedParameter(s.substring(1));
            }
            if (s.contains("$mn_qp:")) {
                // For complex strings with embedded placeholders, we use a lambda-based fallback for now
                // but keep it wrapped in the structured AST.
                return (params, named) -> resolveValueInternal(s, params, named);
            }
            return new CompiledValue.Literal(s);
        }
        if (value instanceof Map<?, ?> vm && vm.size() == 1 && vm.get("$mn_qp") instanceof Integer idx) {
            return new CompiledValue.Parameter(idx);
        }
        return new CompiledValue.Literal(value);
    }

    /**
     * Build a Nitrite Filter from a Map structure.
     *
     * @param entity          the entity metadata
     * @param filterObj       the filter object
     * @param params          positional parameters
     * @param namedParameters named parameters
     * @return the Nitrite Filter
     */
    public Filter buildFilterFromJson(
        final RuntimePersistentEntity<?> entity,
        final Map<String, Object> filterObj,
        final Object[] params,
        final Map<String, Object> namedParameters) {
        return compile(entity, filterObj).bind(params, namedParameters);
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

    private Object resolveValue(Object value, Object[] params, Map<String, Object> namedParameters) {
        Object resolved = resolveValueInternal(value, params, namedParameters);
        if (LOG.isDebugEnabled()) {
            LOG.debug("resolveValue: value={}, resolved={}", value, resolved);
        }
        return resolved;
    }

    private Object resolveValueInternal(Object value, Object[] params, Map<String, Object> namedParameters) {
        if (value instanceof String s) {
            if (s.startsWith("$mn_qp:") && s.indexOf("$mn_qp:", 7) < 0) {
                try {
                    int idx = Integer.parseInt(s.substring(7));
                    if (params != null && idx >= 0 && idx < params.length) {
                        return params[idx];
                    }
                } catch (Exception ignored) {
                }
            }
            if (s.contains("$mn_qp:")) {
                StringBuilder result = new StringBuilder();
                int pos = 0;
                while (pos < s.length()) {
                    int idx = s.indexOf("$mn_qp:", pos);
                    if (idx < 0) {
                        result.append(s.substring(pos));
                        break;
                    }
                    result.append(s.substring(pos, idx));
                    int paramEnd = idx + 7;
                    while (paramEnd < s.length() && Character.isDigit(s.charAt(paramEnd))) {
                        paramEnd++;
                    }
                    try {
                        int paramIdx = Integer.parseInt(s.substring(idx + 7, paramEnd));
                        if (params != null && paramIdx >= 0 && paramIdx < params.length) {
                            Object paramValue = params[paramIdx];
                            result.append(paramValue != null ? paramValue.toString() : "");
                        }
                    } catch (Exception ignored) {
                        result.append(s.substring(idx, paramEnd));
                    }
                    pos = paramEnd;
                }
                return result.toString();
            }
            if (s.startsWith(":")) {
                String name = s.substring(1);
                if (namedParameters.containsKey(name)) {
                    return namedParameters.get(name);
                }
            }
        }
        if (value instanceof Map<?, ?> vm && vm.size() == 1 && vm.get("$mn_qp") instanceof Integer idx) {
            if (params != null && idx >= 0 && idx < params.length) {
                return params[idx];
            }
        }
        return value;
    }

    private Object maybeCoerceUuid(String field, Object value) {
        if (value instanceof String s && ("id".equals(field) || "_id".equals(field))) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return value;
    }

    private Object preConvertForFilter(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return NitriteEntityMapper.epochNanos(instant);
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toEpochDay();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return NitriteEntityMapper.epochNanos(localDateTime.toInstant(ZoneOffset.UTC));
        }
        if (value instanceof LocalTime localTime) {
            return localTime.toNanoOfDay();
        }
        return value;
    }

    private String convertLikeToRegex(String pattern) {
        // Support SQL LIKE wildcards plus the shell-style '*' used by the ported document tests.
        // Keep existing regex constructs intact (for example "J.*"), and anchor the final regex so
        // LIKE behaves as a whole-string match instead of a substring scan.
        StringBuilder regex = new StringBuilder(pattern.length() + 6);
        if (pattern.isEmpty() || pattern.charAt(0) != '^') {
            regex.append('^');
        }
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '%') {
                regex.append(".*");
            } else if (ch == '_') {
                regex.append('.');
            } else if (ch == '*' && (i == 0 || pattern.charAt(i - 1) != '.')) {
                regex.append(".*");
            } else {
                regex.append(ch);
            }
        }
        if (regex.length() == 0 || regex.charAt(regex.length() - 1) != '$') {
            regex.append('$');
        }
        String converted = regex.toString();
        if (!converted.startsWith("(?s)")) {
            converted = "(?s)" + converted;
        }
        return converted;
    }

    /**
     * Builds a filter for a field with operators.
     *
     * @param entity the persistent entity
     * @param rawField the raw field name
     * @param operators the operators map
     * @param params the query parameters
     * @param namedParameters the named parameters
     * @return the built filter
     */
    public Filter buildFieldFilter(
        final RuntimePersistentEntity<?> entity,
        final String rawField,
        final Map<String, Object> operators,
        final Object[] params,
        final Map<String, Object> namedParameters) {

        String field = rawField;
        if (entity != null) {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            if (identity != null && (identity.getName().equals(rawField) || "id".equals(rawField) || "_id".equals(rawField))) {
                field = entityMapper.normalizeFieldName(rawField, entity);
            }

            // Check if this is an association FK field being compared to a non-FK value
            // e.g., author_id compared to "Stephen King" (name) instead of UUID
            // Also handles ONE_TO_MANY reverse lookups (e.g., book_author.title)
            Filter associationFilter = buildAssociationFilter(entity, rawField, operators, params, namedParameters);
            if (associationFilter != null) {
                return associationFilter;
            }

            // For non-association fields with dots, use nested filter
            if (rawField.contains(".")) {
                return buildNestedFilter(entity, rawField, operators, params, namedParameters);
            }

            // Convert camelCase field name to snake_case for regular properties
            // This matches the field naming strategy used when storing documents
            RuntimePersistentProperty<?> prop = entity.getPropertyByName(rawField);
            if (prop != null) {
                field = prop.getPersistedName();
            }
        }

        List<Filter> fieldFilters = new ArrayList<>();
        for (Map.Entry<String, Object> opEntry : operators.entrySet()) {
            String op = opEntry.getKey();
            Object value = resolveValue(opEntry.getValue(), params, namedParameters);
            Object finalValue = entityMapper.toNitriteFilterValue(preConvertForFilter(maybeCoerceUuid(field, value)), rawField);
            Filter f = buildOperatorFilter(entity, field, op, finalValue, params, namedParameters);
            if (f != null && f != Filter.ALL) {
                fieldFilters.add(f);
            }
        }
        if (fieldFilters.isEmpty()) {
            return Filter.ALL;
        }
        return fieldFilters.size() == 1 ? fieldFilters.get(0) : Filter.and(fieldFilters.toArray(new Filter[0]));
    }

    /**
     * Detect and handle association FK fields being compared to non-FK values.
     * For example, when author_id is compared to "Stephen King" (a name) instead of a UUID.
     * Also handles ONE_TO_MANY reverse lookups (e.g., book_author compared to "The Stand").
     *
     * @param entity the entity metadata
     * @param field the field name (e.g., "author_id")
     * @param operators the filter operators
     * @param params query parameters
     * @param namedParameters named parameters
     * @return a Filter if this is an association FK mismatch, null otherwise
     */
    private Filter buildAssociationFilter(
        final RuntimePersistentEntity<?> entity,
        final String field,
        final Map<String, Object> operators,
        final Object[] params,
        final Map<String, Object> namedParameters) {

        // Only handle $eq operator for now
        if (!operators.containsKey("$eq")) {
            return null;
        }

        // Fast path: if the field is a standard non-association property, it doesn't need fallback logic.
        RuntimePersistentProperty<?> directProp = entity.getPropertyByName(field);
        if (directProp != null && !(directProp instanceof RuntimeAssociation<?>)) {
            return null;
        }

        // Get the value being compared
        Object value = resolveValue(operators.get("$eq"), params, namedParameters);
        if (value == null) {
            return null;
        }

        // Find the association that this field belongs to
        RuntimeAssociation<?> association = null;
        boolean isReverseLookup = false;

        // First, check for direct MANY_TO_ONE associations (field matches FK)
        for (RuntimePersistentProperty<?> prop : entity.getPersistentProperties()) {
            if (prop instanceof RuntimeAssociation<?> assoc) {
                Relation.Kind kind = assoc.getKind();
                // Only handle MANY_TO_ONE here - ONE_TO_MANY is handled as reverse lookup
                if (kind != Relation.Kind.MANY_TO_ONE) {
                    continue;
                }
                // The persisted name is already the FK field name (e.g., "author_id")
                String assocFkField = assoc.getPersistedName();
                if (assocFkField.equals(field)) {
                    association = assoc;
                    break;
                }
            }
        }

        // If not found, check for ONE_TO_MANY reverse lookups
        // Field name patterns:
        // - New format: "<persisted_association_name>.<property>" (e.g., "book_author.title")
        // - Old format: "<persisted_association_name>_<property>" (e.g., "books_title")
        // - Lost property: field equals persisted name exactly (e.g., "book_author")
        if (association == null && (field.contains("_") || field.contains("."))) {
            for (RuntimePersistentProperty<?> p : entity.getPersistentProperties()) {
                if (p instanceof RuntimeAssociation<?> assoc) {
                    Relation.Kind kind = assoc.getKind();
                    if (kind == Relation.Kind.ONE_TO_MANY ||
                        kind == Relation.Kind.MANY_TO_MANY) {

                        String persistedName = assoc.getPersistedName();
                        // Use the associated entity's singular name for matching (handles irregular plurals like "cities" -> "city")
                        String singularName = assoc.getAssociatedEntity().getSimpleName();
                        String decapitalizedName = assoc.getAssociatedEntity().getDecapitalizedName();

                        // New format: "book_author.title"
                        if (field.contains(".")) {
                            String assocPart = field.substring(0, field.indexOf('.'));
                            if (assocPart.equals(persistedName) || assocPart.equals(singularName) || assocPart.equals(decapitalizedName)) {
                                association = assoc;
                                isReverseLookup = true;
                                break;
                            }
                        }

                        // Old format: "books_title" or "book_title"
                        if (field.startsWith(persistedName + "_") || field.startsWith(singularName + "_") || field.startsWith(decapitalizedName + "_")) {
                            association = assoc;
                            isReverseLookup = true;
                            break;
                        }

                        // Lost property: field equals persistedName exactly
                        if (field.equals(persistedName)) {
                            association = assoc;
                            isReverseLookup = true;
                            break;
                        }
                    }
                }
            }
        }

        if (association == null) {
            return null;
        }

        // Check if the value looks like it should trigger association resolution
        RuntimePersistentProperty<?> assocIdentity = association.getAssociatedEntity().getIdentity();
        if (assocIdentity == null) {
            return null;
        }

        // Determine if we should use sub-query for association resolution
        boolean useSubQuery = false;

        if (value instanceof String strValue) {
            if (assocIdentity.getType() == java.util.UUID.class) {
                // For UUID IDs, check if value looks like a UUID
                useSubQuery = !looksLikeId(strValue, assocIdentity.getType());
            } else if (assocIdentity.getType() == String.class) {
                // For String IDs, use heuristic: values with spaces are likely not IDs
                useSubQuery = strValue.contains(" ");
            }
        }

        if (!useSubQuery) {
            return null;
        }

        // Use sub-query to resolve the association
        if (subQueryExecutor == null) {
            return null;
        }

        if (isReverseLookup) {
            // Handle ONE_TO_MANY reverse lookup
            // e.g., Author.books.title = "The Stand"
            // 1. Query Books where property = "The Stand"
            // 2. Extract author_ids (the back-reference FK)
            // 3. Filter Authors where id IN (author_ids)

            RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
            String targetPropertyName = null;

            // The property that points back to the main entity (needed early for disambiguation)
            String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
            if (mappedBy == null) {
                return null;
            }

            // Try to extract property name from field suffix
            // New format: "book_author.title" (dot separator from query builder)
            // Old format: "book_author_title" or "book_author" (underscore separator)
            String persistedName = association.getPersistedName();
            // Use the associated entity's singular name for matching (handles irregular plurals like "cities" -> "city")
            String singularName = association.getAssociatedEntity().getSimpleName();
            String decapitalizedName = association.getAssociatedEntity().getDecapitalizedName();

            if (field.contains(".")) {
                // New format: "book_author.title" - extract property after last dot
                int lastDot = field.lastIndexOf('.');
                String assocPart = field.substring(0, lastDot);
                targetPropertyName = field.substring(lastDot + 1);
                // Verify the association part matches
                if (!assocPart.equals(persistedName) && !assocPart.equals(singularName) && !assocPart.equals(decapitalizedName)) {
                    targetPropertyName = null;  // Doesn't match, will fall through to other checks
                }
            } else if (field.startsWith(persistedName + "_")) {
                targetPropertyName = field.substring(persistedName.length() + 1);
            } else if (field.startsWith(singularName + "_")) {
                targetPropertyName = field.substring(singularName.length() + 1);
            } else if (field.startsWith(decapitalizedName + "_")) {
                targetPropertyName = field.substring(decapitalizedName.length() + 1);
            } else if (field.equals(persistedName)) {
                // Old format without property suffix - property name was lost
                // Will use $or fallback below
                targetPropertyName = null;
            }

            RuntimePersistentProperty<?> targetProperty = null;

            // Don't use the mappedBy as the target property - it's the back-reference, not what we're filtering by
            if (targetPropertyName != null && !targetPropertyName.equals(mappedBy)) {
                targetProperty = associatedEntity.getPropertyByName(targetPropertyName);
                if (targetProperty == null) {
                    for (RuntimePersistentProperty<?> p : associatedEntity.getPersistentProperties()) {
                        if (p.getPersistedName().equals(targetPropertyName)) {
                            targetProperty = p;
                            break;
                        }
                    }
                }
            }

            // Query builder should have preserved the property path for Criteria queries
            if (targetProperty == null) {
                return null;
            }

            RuntimePersistentProperty<?> backRefProp = associatedEntity.getPropertyByName(mappedBy);
            if (backRefProp == null) {
                return null;
            }
            String backRefPersistedName = backRefProp.getPersistedName();

            // Build sub-query filter for the associated entity
            Map<String, Object> subFilterMap = Collections.singletonMap(
                targetProperty.getPersistedName(),
                Collections.singletonMap("$eq", value)
            );

            if (LOG.isDebugEnabled()) {
                LOG.debug("Reverse lookup sub-query: entity={}, filter={}, backRef={}",
                    associatedEntity.getName(), subFilterMap, backRefPersistedName);
            }

            // Execute sub-query to get matching values for the back-reference field
            List<Object> matchingValues = subQueryExecutor.executeSubQuery(
                associatedEntity, subFilterMap, backRefPersistedName, params, namedParameters);

            if (matchingValues.isEmpty()) {
                return NONE;
            }

            // Filter main entity by ID IN (matchingValues)
            String idField = entity.getIdentity().getPersistedName();
            Comparable<?>[] comparableIds = matchingValues.stream()
                .filter(java.util.Objects::nonNull)
                .map(id -> id instanceof Comparable<?> ? (Comparable<?>) id : id.toString())
                .toArray(Comparable<?>[]::new);
            return comparableIds.length == 0 ? NONE : FluentFilter.where(idField).in(comparableIds);
        } else {
            // Handle MANY_TO_ONE forward lookup
            // e.g., Book.author.name = "Stephen King"
            // For Criteria queries, query builder generates: author_id.name = "Stephen King"
            // This path is for legacy format where only FK field is present
            // Build $or filter across all compatible properties on the associated entity
            RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
            List<Map<String, Object>> orClauses = new ArrayList<>();
            for (RuntimePersistentProperty<?> p : associatedEntity.getPersistentProperties()) {
                if (p.getType().isInstance(value)) {
                    orClauses.add(Collections.singletonMap(
                        p.getPersistedName(),
                        Collections.singletonMap("$eq", value)
                    ));
                }
            }
            if (orClauses.isEmpty()) {
                return null;
            }

            // Build sub-query filter for the associated entity
            Map<String, Object> subFilterMap = Collections.singletonMap("$or", orClauses);

            // Execute sub-query to get matching IDs
            List<Object> matchingIds = subQueryExecutor.executeSubQuery(
                associatedEntity, subFilterMap, null, params, namedParameters);

            if (matchingIds.isEmpty()) {
                return NONE;
            }

            // Filter main entity by association ID IN (matchingIds)
            Comparable<?>[] comparableIds = matchingIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(id -> id instanceof Comparable<?> ? (Comparable<?>) id : id.toString())
                .toArray(Comparable<?>[]::new);
            return comparableIds.length == 0 ? NONE : FluentFilter.where(field).in(comparableIds);
        }
    }

    /**
     * Check if a string value looks like a valid ID of the given type.
     * For UUIDs, checks the standard UUID format.
     */
    private boolean looksLikeId(String value, Class<?> idType) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (idType == java.util.UUID.class) {
            // Check for standard UUID format: 8-4-4-4-12 hex digits
            if (value.length() == 36 && value.charAt(8) == '-' && value.charAt(13) == '-' &&
                value.charAt(18) == '-' && value.charAt(23) == '-') {
                try {
                    java.util.UUID.fromString(value);
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            return false;
        }

        // For String IDs, assume any non-empty string is valid
        return true;
    }

    private Filter buildNestedFilter(
        final RuntimePersistentEntity<?> entity,
        final String fieldPath,
        final Map<String, Object> operators,
        final Object[] params,
        final Map<String, Object> namedParameters) {

        int dotIdx = fieldPath.indexOf('.');
        String firstPart = fieldPath.substring(0, dotIdx);
        String remaining = fieldPath.substring(dotIdx + 1);

        RuntimePersistentProperty<?> prop = null;
        if (entity != null) {
            prop = entity.getPropertyByName(firstPart);
            if (prop == null) {
                // Try finding by persisted name
                for (RuntimePersistentProperty<?> p : entity.getPersistentProperties()) {
                    if (p.getPersistedName().equals(firstPart)) {
                        prop = p;
                        break;
                    }
                }
            }
            if (prop == null) {
                RuntimePersistentProperty<?> identity = entity.getIdentity();
                if (identity != null && (identity.getName().equals(firstPart) || identity.getPersistedName().equals(firstPart))) {
                    prop = identity;
                }
            }
        }

        String fieldName = prop != null ? prop.getPersistedName() : firstPart;

        if (prop instanceof RuntimeAssociation<?> assoc) {
            Relation.Kind kind = assoc.getKind();
            boolean isCollection = kind == Relation.Kind.ONE_TO_MANY ||
                                 kind == Relation.Kind.MANY_TO_MANY;
            boolean isManyToOne = kind == Relation.Kind.MANY_TO_ONE;

            RuntimePersistentEntity<?> associatedEntity = assoc.getAssociatedEntity();
            if (isCollection) {
                // For ONE_TO_MANY and MANY_TO_MANY, use sub-query approach
                // Nitrite stores these as foreign keys in child documents, not embedded arrays
                if (subQueryExecutor == null) {
                    // Fallback to elemMatch if no sub-query executor (will fail for Nitrite but maintains API)
                    Filter subFilter = buildFieldFilter(associatedEntity, remaining, operators, params, namedParameters);
                    return FluentFilter.where(fieldName).elemMatch(subFilter);
                }

                // Build sub-query filter map with the property name (remaining) preserved
                Map<String, Object> subFilterMap = new java.util.LinkedHashMap<>();
                Map<String, Object> resolvedOperators = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : operators.entrySet()) {
                    Object resolvedValue = resolveValue(entry.getValue(), params, namedParameters);
                    resolvedOperators.put(entry.getKey(), resolvedValue);
                }
                subFilterMap.put(remaining, resolvedOperators);

                // Get the mappedBy property (the FK in the child entity pointing back to parent)
                String mappedBy = assoc.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
                if (mappedBy == null) {
                    if (kind == Relation.Kind.MANY_TO_MANY) {
                        // MANY_TO_MANY owner side: IDs are stored in the document field 'fieldName'
                        // Find IDs of associated entities matching the filter
                        List<Object> matchingIds = subQueryExecutor.executeSubQuery(
                            associatedEntity, subFilterMap, null, params, namedParameters);
                        if (matchingIds.isEmpty()) {
                            return NONE;
                        }
                        // Filter current document where 'fieldName' (the collection) contains any of matchingIds
                        // Standard Nitrite filters might not consistently handle 'contains' for non-indexed collections.
                        return pair -> {
                            org.dizitart.no2.collection.Document doc = pair.getSecond();
                            Object val = doc.get(fieldName);
                            if (val instanceof java.util.Collection<?> coll) {
                                for (Object id : matchingIds) {
                                    if (coll.contains(id)) {
                                        return true;
                                    }
                                }
                            }
                            return false;
                        };
                    }
                    return NONE;
                }

                RuntimePersistentProperty<?> backRefProp = associatedEntity.getPropertyByName(mappedBy);
                if (backRefProp == null) {
                    return NONE;
                }
                String backRefPersistedName = backRefProp.getPersistedName();

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Collection reverse lookup: entity={}, filter={}, backRef={}",
                        associatedEntity.getName(), subFilterMap, backRefPersistedName);
                }

                // Execute sub-query to get matching values for the back-reference field
                List<Object> matchingValues = subQueryExecutor.executeSubQuery(
                    associatedEntity, subFilterMap, backRefPersistedName, params, namedParameters);

                if (matchingValues.isEmpty()) {
                    return NONE;
                }

                // Filter main entity by ID IN (matchingValues)
                String idField = entity.getIdentity().getPersistedName();
                Comparable<?>[] comparableIds = matchingValues.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(id -> id instanceof Comparable<?> ? (Comparable<?>) id : id.toString())
                    .toArray(Comparable<?>[]::new);
                return comparableIds.length == 0 ? NONE : FluentFilter.where(idField).in(comparableIds);
            } else if (isManyToOne && subQueryExecutor != null) {
                // For MANY_TO_ONE, execute sub-query on associated entity and join by ID
                // Build sub-query filter map with the property name (remaining) preserved
                Map<String, Object> subFilterMap = new java.util.LinkedHashMap<>();
                Map<String, Object> resolvedOperators = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : operators.entrySet()) {
                    Object resolvedValue = resolveValue(entry.getValue(), params, namedParameters);
                    resolvedOperators.put(entry.getKey(), resolvedValue);
                }
                subFilterMap.put(remaining, resolvedOperators);
                // Execute sub-query to get matching IDs
                List<Object> matchingIds = subQueryExecutor.executeSubQuery(
                    associatedEntity, subFilterMap, null, params, namedParameters);
                if (matchingIds.isEmpty()) {
                    return NONE; // No matches, return filter that matches nothing
                }
                // Filter main entity by association ID IN (matchingIds)
                // Convert to Comparable array for FluentFilter.where().in()
                Comparable<?>[] comparableIds = matchingIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(id -> id instanceof Comparable<?> ? (Comparable<?>) id : id.toString())
                    .toArray(Comparable<?>[]::new);
                return comparableIds.length == 0 ? NONE : FluentFilter.where(fieldName).in(comparableIds);
            } else {
                return buildOperatorFiltersForPath(entity, fieldName + "." + remaining, operators, params, namedParameters);
            }
        }

        if (prop != null) {
            return buildOperatorFiltersForPath(entity, prop.getName() + "." + snakeToCamelPath(remaining), operators, params, namedParameters);
        }

        // Fallback to dot notation
        return buildOperatorFiltersForPath(entity, snakeToCamelPath(fieldPath), operators, params, namedParameters);
    }

    private String snakeToCamelPath(String path) {
        if (path == null || path.indexOf('_') < 0) {
            return path;
        }
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = snakeToCamel(parts[i]);
        }
        return String.join(".", parts);
    }

    private String snakeToCamel(String value) {
        if (value == null || value.indexOf('_') < 0) {
            return value;
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Convert operators Map to a filterMap for sub-query execution.
     * This is a simplified conversion for basic equality filters.
     */
    private Map<String, Object> filterToMap(Map<String, Object> operators) {
        Map<String, Object> filterMap = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : operators.entrySet()) {
            filterMap.put(entry.getKey(), entry.getValue());
        }
        return filterMap;
    }

    private Filter buildOperatorFiltersForPath(
        final RuntimePersistentEntity<?> entity,
        final String fullPath,
        final Map<String, Object> operators,
        final Object[] params,
        final Map<String, Object> namedParameters) {

        List<Filter> fieldFilters = new ArrayList<>();
        for (Map.Entry<String, Object> opEntry : operators.entrySet()) {
            String op = opEntry.getKey();
            Object value = resolveValue(opEntry.getValue(), params, namedParameters);
            Object finalValue = entityMapper.toNitriteFilterValue(preConvertForFilter(maybeCoerceUuid(fullPath, value)), fullPath);
            Filter f = buildOperatorFilter(entity, fullPath, op, finalValue, params, namedParameters);
            if (f != null && f != Filter.ALL) {
                fieldFilters.add(f);
            }
        }
        return fieldFilters.size() == 1 ? fieldFilters.get(0) : Filter.and(fieldFilters.toArray(new Filter[0]));
    }

    public Filter buildOperatorFilter(
        final RuntimePersistentEntity<?> entity,
        final String field,
        final String op,
        final Object finalValue,
        final Object[] params,
        final Map<String, Object> namedParameters) {
        return switch (op) {
            case "$eq" -> entityMapper.eqWithNumericCoercion(entity, field, finalValue, field);
            case "$ne" -> FluentFilter.where(field).notEq(finalValue);
            case "$gt" -> finalValue instanceof Comparable<?> c ? FluentFilter.where(field).gt(c) : Filter.ALL;
            case "$gte" -> finalValue instanceof Comparable<?> c ? FluentFilter.where(field).gte(c) : Filter.ALL;
            case "$lt" -> finalValue instanceof Comparable<?> c ? FluentFilter.where(field).lt(c) : Filter.ALL;
            case "$lte" -> finalValue instanceof Comparable<?> c ? FluentFilter.where(field).lte(c) : Filter.ALL;
            case "$in" -> buildInFilter(field, finalValue, params, namedParameters);
            case "$nin" -> buildNotInFilter(field, finalValue, params, namedParameters);
            case "$null" -> Boolean.TRUE.equals(finalValue) ? FluentFilter.where(field).eq(null) : Filter.ALL;
            case "$notNull" -> Boolean.TRUE.equals(finalValue) ? FluentFilter.where(field).notEq(null) : Filter.ALL;
            case "$between" -> {
                if (finalValue instanceof List<?> list && list.size() == 2) {
                    Object v1 = entityMapper.toFilterValue(preConvertForFilter(resolveValue(list.get(0), params, namedParameters)));
                    Object v2 = entityMapper.toFilterValue(preConvertForFilter(resolveValue(list.get(1), params, namedParameters)));
                    yield FluentFilter.where(field).between((Comparable<?>) v1, (Comparable<?>) v2);
                }
                yield Filter.ALL;
            }
            case "$regex" -> {
                Object resolved = resolveValue(finalValue, params, namedParameters);
                yield FluentFilter.where(field).regex(resolveRegexPattern(resolved));
            }
            case "$like" -> {
                Object resolved = resolveValue(finalValue, params, namedParameters);
                yield FluentFilter.where(field).regex(resolved != null ? convertLikeToRegex(resolved.toString()) : "");
            }
            case "$not" -> finalValue instanceof Map<?, ?> m ? buildFieldFilter(entity, field, toStringObjectMap(m), params, namedParameters).not() : Filter.ALL;
            case "$exists" -> Boolean.TRUE.equals(finalValue) ? FluentFilter.where(field).notEq(null) : FluentFilter.where(field).eq(null);
            case "$empty" -> Boolean.TRUE.equals(finalValue) ? Filter.or(FluentFilter.where(field).eq(""), FluentFilter.where(field).eq(null)) : Filter.and(FluentFilter.where(field).notEq(""), FluentFilter.where(field).notEq(null));
            case "$text" -> FluentFilter.where(field).text(finalValue != null ? finalValue.toString() : "");
            case "$near" -> buildNearFilter(field, finalValue, params, namedParameters);
            case "$within" -> createSpatialFilter(field, finalValue, "within");
            case "$intersects" -> createSpatialFilter(field, finalValue, "intersects");
            default -> FluentFilter.where(field).eq(finalValue);
        };
    }

    private String resolveRegexPattern(Object resolved) {
        if (resolved == null) {
            return "";
        }
        if (resolved instanceof java.util.regex.Pattern pattern) {
            return pattern.pattern();
        }
        String value = resolved.toString();
        if (value.length() >= 2 && value.startsWith("/") && value.endsWith("/")) {
            return value.substring(1, value.length() - 1);
        }
        // Derived LIKE/CONTAINING methods are emitted as $regex with wildcard-like text payloads.
        if (looksLikeWildcardPattern(value)) {
            return convertLikeToRegex(value);
        }
        return value;
    }

    private boolean looksLikeWildcardPattern(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        // Keep explicit regexes untouched (for example "^J", "J.*", "[A-Z]").
        if (value.startsWith("^")
            || value.endsWith("$")
            || value.contains("[")
            || value.contains("(")
            || value.contains("|")
            || value.contains("\\")) {
            return false;
        }
        return value.indexOf('%') >= 0 || value.indexOf('_') >= 0 || value.indexOf('*') >= 0;
    }

    private Filter buildInFilter(String field, Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        if (finalValue == null) {
            return NONE;
        }
        List<Comparable<?>> resolvedValues = resolveCollection(finalValue, params, namedParameters);
        return resolvedValues.isEmpty() ? NONE : FluentFilter.where(field).in(resolvedValues.toArray(new Comparable[0]));
    }

    private Filter buildNotInFilter(String field, Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        if (finalValue == null) {
            return Filter.ALL;
        }
        List<Comparable<?>> resolvedValues = resolveCollection(finalValue, params, namedParameters);
        return resolvedValues.isEmpty() ? Filter.ALL : FluentFilter.where(field).notIn(resolvedValues.toArray(new Comparable[0]));
    }

    private List<Comparable<?>> resolveCollection(Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        List<Comparable<?>> resolvedValues = new ArrayList<>();
        if (finalValue instanceof List<?> list && list.size() == 1 && isPlaceholder(list.get(0))) {
            Object resolved = resolveValue(list.get(0), params, namedParameters);
            if (resolved instanceof Collection<?> coll) {
                for (Object collItem : coll) {
                    Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(collItem), null);
                    if (itemResolved instanceof Comparable<?> c) {
                        resolvedValues.add(c);
                    }
                }
            } else if (resolved != null && resolved.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(resolved);
                for (int i = 0; i < len; i++) {
                    Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(java.lang.reflect.Array.get(resolved, i)), null);
                    if (itemResolved instanceof Comparable<?> c) {
                        resolvedValues.add(c);
                    }
                }
            } else if (resolved instanceof Comparable<?> c) {
                resolvedValues.add(c);
            }
        } else if (finalValue instanceof Collection<?> coll) {
            for (Object item : coll) {
                Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(item, params, namedParameters)), null);
                if (itemResolved instanceof Comparable<?> c) {
                    resolvedValues.add(c);
                }
            }
        } else if (finalValue instanceof Object[] array) {
            for (Object item : array) {
                Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(item, params, namedParameters)), null);
                if (itemResolved instanceof Comparable<?> c) {
                    resolvedValues.add(c);
                }
            }
        }
        return resolvedValues;
    }

    private Filter buildNearFilter(String field, Object value, Object[] params, Map<String, Object> namedParameters) {
        if (value instanceof Map<?, ?> m) {
            Object center = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(m.get("center"), params, namedParameters)), field);
            Object distanceObj = resolveValue(m.get("distance"), params, namedParameters);
            double distance = distanceObj instanceof Number n ? n.doubleValue() : 0.0;
            
            // Use reflection to create spatial filters (nitrite-spatial is optional)
            if (ClassUtils.isPresent(SPATIAL_FLUENT_FILTER_CLASS, null)) {
                try {
                    Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                    Method whereMethod = spatialClass.getMethod("where", String.class);
                    Object spatialFluentFilter = whereMethod.invoke(null, field);
                    
                    // Handle GeoPoint (via reflection to avoid hard dependency)
                    if (center != null && ClassUtils.isPresent(GEO_POINT_CLASS, null)) {
                        Class<?> geoPointClass = Class.forName(GEO_POINT_CLASS);
                        if (geoPointClass.isInstance(center)) {
                            Object point = geoPointClass.getMethod("getPoint").invoke(center);
                            Method nearMethod = spatialFluentFilter.getClass().getMethod("near", Class.forName(JTS_POINT_CLASS), Double.class);
                            return (Filter) nearMethod.invoke(spatialFluentFilter, point, distance);
                        }
                    }
                    
                    // Handle JTS Point
                    if (center != null && ClassUtils.isPresent(JTS_POINT_CLASS, null)) {
                        Class<?> pointClass = Class.forName(JTS_POINT_CLASS);
                        if (pointClass.isInstance(center)) {
                            Method nearMethod = spatialFluentFilter.getClass().getMethod("near", pointClass, Double.class);
                            return (Filter) nearMethod.invoke(spatialFluentFilter, center, distance);
                        }
                    }
                    
                    // Handle JTS Geometry - extract coordinate
                    if (center != null && ClassUtils.isPresent(JTS_GEOMETRY_CLASS, null)) {
                        Class<?> geometryClass = Class.forName(JTS_GEOMETRY_CLASS);
                        if (geometryClass.isInstance(center)) {
                            Method getCoordMethod = geometryClass.getMethod("getCoordinate");
                            Object coord = getCoordMethod.invoke(center);
                            if (coord != null) {
                                Method nearMethod = spatialFluentFilter.getClass().getMethod("near", Class.forName(JTS_COORDINATE_CLASS), Double.class);
                                return (Filter) nearMethod.invoke(spatialFluentFilter, coord, distance);
                            }
                        }
                    }
                    
                    // Fallback for Coordinate
                    if (center != null && ClassUtils.isPresent(JTS_COORDINATE_CLASS, null)) {
                        Class<?> coordClass = Class.forName(JTS_COORDINATE_CLASS);
                        if (coordClass.isInstance(center)) {
                            Method nearMethod = spatialFluentFilter.getClass().getMethod("near", coordClass, Double.class);
                            return (Filter) nearMethod.invoke(spatialFluentFilter, center, distance);
                        }
                    }

                    if (center != null) {
                        throw new DataAccessException("Unsupported center type for $near spatial filter: " + center.getClass().getName());
                    }
                } catch (DataAccessException e) {
                    throw e;
                } catch (Exception e) {
                    throw new DataAccessException("Failed to create $near spatial filter on field '" + field + "': " + e.getMessage(), e);
                }
            }
        }
        return Filter.ALL;
    }

    private Filter createSpatialFilter(String field, Object geometry, String method) {
        if (geometry == null) {
            return Filter.ALL;
        }
        
        // Use reflection to create spatial filters (nitrite-spatial is optional)
        if (ClassUtils.isPresent(SPATIAL_FLUENT_FILTER_CLASS, null)) {
            try {
                Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                Method whereMethod = spatialClass.getMethod("where", String.class);
                Object spatialFluentFilter = whereMethod.invoke(null, field);
                
                // Handle GeoPoint for within/intersects (convert to Point via reflection)
                if (ClassUtils.isPresent(GEO_POINT_CLASS, null)) {
                    Class<?> geoPointClass = Class.forName(GEO_POINT_CLASS);
                    if (geoPointClass.isInstance(geometry)) {
                        Object point = geoPointClass.getMethod("getPoint").invoke(geometry);
                        Method filterMethod = spatialFluentFilter.getClass().getMethod(method, Class.forName(JTS_POINT_CLASS));
                        return (Filter) filterMethod.invoke(spatialFluentFilter, point);
                    }
                }
                
                // Handle JTS Geometry directly
                if (ClassUtils.isPresent(JTS_GEOMETRY_CLASS, null)) {
                    Class<?> geometryClass = Class.forName(JTS_GEOMETRY_CLASS);
                    if (geometryClass.isInstance(geometry)) {
                        Method filterMethod = spatialFluentFilter.getClass().getMethod(method, geometryClass);
                        return (Filter) filterMethod.invoke(spatialFluentFilter, geometry);
                    }
                }

                throw new DataAccessException("Unsupported geometry type for $" + method + " spatial filter: " + geometry.getClass().getName());
            } catch (DataAccessException e) {
                throw e;
            } catch (Exception e) {
                throw new DataAccessException("Failed to create $" + method + " spatial filter on field '" + field + "': " + e.getMessage(), e);
            }
        }

        return Filter.ALL;
    }

    /**
     * Functional interface for executing sub-queries on associated entities.
     * Used for auto-join on MANY_TO_ONE associations.
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
                                     String targetField,
                                     Object[] params,
                                     Map<String, Object> namedParameters);
    }
}
