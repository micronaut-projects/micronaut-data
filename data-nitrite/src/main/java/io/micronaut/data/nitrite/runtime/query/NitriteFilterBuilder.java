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
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builder for Nitrite Filters from JSON-like structures.
 *
 * @since 1.0.0
 */
@Internal
public final class NitriteFilterBuilder {

    private static final String SPATIAL_FLUENT_FILTER_CLASS = "org.dizitart.no2.spatial.SpatialFluentFilter";
    private static final String GEOMETRY_CLASS = "org.locationtech.jts.geom.Geometry";

    /**
     * A filter that matches no documents (used for empty IN clauses).
     */
    private static final Filter NONE = element -> false;

    private final NitriteEntityMapper entityMapper;

    /**
     * Create a new filter builder.
     *
     * @param entityMapper the entity mapper
     */
    public NitriteFilterBuilder(NitriteEntityMapper entityMapper) {
        this.entityMapper = entityMapper;
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
        if (filterObj == null || filterObj.isEmpty()) {
            return Filter.ALL;
        }
        List<Filter> filters = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filterObj.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // Skip non-filter metadata keys that can appear in the JSON encoding.
            // ($and/$or are handled below and are real filter operators.)
            if (key != null && (key.equals("$sort")
                || key.equals("$set")
                || key.equals("$limit")
                || key.equals("$skip")
                || key.equals("$count"))) {
                continue;
            }
            if (key.equals("$and")) {
                if (value instanceof List<?> list) {
                    List<Filter> andFilters = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Filter f = buildFilterFromJson(entity, toStringObjectMap(m), params, namedParameters);
                            if (f != null && f != Filter.ALL) {
                                andFilters.add(f);
                            }
                        }
                    }
                    if (!andFilters.isEmpty()) {
                        filters.add(Filter.and(andFilters.toArray(new Filter[0])));
                    }
                }
            } else if (key.equals("$or")) {
                if (value instanceof List<?> list) {
                    List<Filter> orFilters = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Filter f = buildFilterFromJson(entity, toStringObjectMap(m), params, namedParameters);
                            if (f != null && f != Filter.ALL) {
                                orFilters.add(f);
                            }
                        }
                    }
                    if (!orFilters.isEmpty()) {
                        filters.add(Filter.or(orFilters.toArray(new Filter[0])));
                    }
                }
            } else {
                Object resolvedValue = resolveValue(value, params, namedParameters);
                if (resolvedValue instanceof Map<?, ?> m && !isPlaceholder(m)) {
                    Filter f = buildFieldFilter(entity, key, toStringObjectMap(m), params, namedParameters);
                    if (f != null && f != Filter.ALL) {
                        filters.add(f);
                    }
                } else {
                    Filter f = buildFieldFilter(entity, key, Collections.singletonMap("$eq", resolvedValue), params, namedParameters);
                    if (f != null && f != Filter.ALL) {
                        filters.add(f);
                    }
                }
            }
        }
        if (filters.isEmpty()) {
            return Filter.ALL;
        }
        return filters.size() == 1 ? filters.get(0) : Filter.and(filters.toArray(new Filter[0]));
    }

    /**
     * Check if a value is a parameter placeholder.
     *
     * @param value the value to check
     * @return true if it's a placeholder
     */
    private boolean isPlaceholder(Object value) {
        if (value instanceof String s && (s.startsWith("$mn_qp:") || s.startsWith(":"))) {
            return true;
        }
        return value instanceof Map<?, ?> vm && vm.size() == 1 && vm.containsKey("$mn_qp");
    }

    /**
     * Convert a Map<?, ?> to Map<String, Object>.
     *
     * @param map the map to convert
     * @return the converted map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /**
     * Resolve a value that may contain embedded parameter placeholders.
     * <p>
     * Handles string values with {@code $mn_qp:N} placeholders (e.g., regex patterns like
     * {@code "(?i).*:0.*"}), replacing them with actual parameter values at runtime.
     * For collection parameters, resolves the placeholder to the actual collection.
     *
     * @param value the value to resolve (may contain placeholders)
     * @param params positional parameters
     * @param namedParameters named parameters
     * @return the resolved value
     */
    private Object resolveValue(Object value, Object[] params, Map<String, Object> namedParameters) {
        if (value instanceof String s) {
            // Check if the string is EXACTLY a placeholder (e.g., "$mn_qp:0")
            // In this case, return the parameter value directly (even if null)
            if (s.startsWith("$mn_qp:") && s.indexOf("$mn_qp:", 7) < 0) {
                try {
                    int idx = Integer.parseInt(s.substring(7));
                    if (params != null && idx >= 0 && idx < params.length) {
                        return params[idx];  // May return null for null collection parameters
                    }
                } catch (Exception ignored) {
                    // ignore parse exception
                }
            }
            // Check if the string contains embedded placeholders (e.g., regex patterns)
            if (s.contains("$mn_qp:")) {
                // Replace all $mn_qp:N placeholders with their values
                StringBuilder result = new StringBuilder();
                int pos = 0;
                while (pos < s.length()) {
                    int idx = s.indexOf("$mn_qp:", pos);
                    if (idx < 0) {
                        result.append(s.substring(pos));
                        break;
                    }
                    result.append(s.substring(pos, idx));
                    // Find the end of the parameter index (digits only)
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

    /**
     * Coerce a field value to UUID if appropriate.
     *
     * @param field the field name
     * @param value the raw value
     * @return the potentially coerced value
     */
    private Object maybeCoerceUuid(String field, Object value) {
        if (value instanceof String s && ("id".equals(field) || "_id".equals(field))) {
            try {
                return UUID.fromString(s);
            } catch (Exception ignored) {
            }
        }
        return value;
    }

    /**
     * Pre-convert temporal types to match Jackson serialization format.
     * This ensures query values match the format stored by JacksonMapper with JavaTimeModule
     * and WRITE_DATES_AS_TIMESTAMPS disabled.
     *
     * @param value the value to pre-convert
     * @return the pre-converted value
     */
    private Object preConvertForFilter(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            // Convert to ISO string format to match Jackson serialization
            // when WRITE_DATES_AS_TIMESTAMPS is disabled
            return instant.toString();
        }
        if (value instanceof LocalDate localDate) {
            // Convert to ISO string format to match Jackson serialization
            return localDate.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            // Convert to ISO string format to match Jackson serialization
            return localDateTime.toString();
        }
        if (value instanceof LocalTime localTime) {
            // Convert to ISO string format to match Jackson serialization
            return localTime.toString();
        }
        return value;
    }

    /**
     * Build a Nitrite Filter for a specific field.
     *
     * @param entity          the entity metadata
     * @param rawField        the field name
     * @param operators       the operator map
     * @param params          positional parameters
     * @param namedParameters named parameters
     * @return the Nitrite Filter
     */
    public Filter buildFieldFilter(
        final RuntimePersistentEntity<?> entity,
        final String rawField,
        final Map<String, Object> operators,
        final Object[] params,
        final Map<String, Object> namedParameters) {
        String field = entityMapper.normalizeFieldName(rawField, entity);
        
        // Handle EmbeddedId expansion
        if (entity != null) {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            if (identity != null && identity.isAnnotationPresent(EmbeddedId.class) && 
                (identity.getName().equals(field) || "id".equals(field) || "_id".equals(field))) {
                
                // If it's the identity property being queried, we need to expand it 
                // if the value is an object (the ID object itself).
                Object val = operators.get("$eq");
                if (val != null) {
                    Object resolved = resolveValue(val, params, namedParameters);
                    if (resolved != null && !entityMapper.isSimpleType(resolved.getClass())) {
                        return entityMapper.idEqualsFilter(entity.getIntrospection().getBeanType(), resolved);
                    }
                }
            }
        }

        List<Filter> fieldFilters = new ArrayList<>();
        for (Map.Entry<String, Object> opEntry : operators.entrySet()) {
            String op = opEntry.getKey();
            Object value = resolveValue(opEntry.getValue(), params, namedParameters);
            Object coercedValue = maybeCoerceUuid(field, value);
            Object preConverted = preConvertForFilter(coercedValue);
            Object finalValue = entityMapper.toNitriteFilterValue(preConverted, rawField);
            Filter f = switch (op) {
                case "$eq" -> entityMapper.eqWithNumericCoercion(entity, field, finalValue, field);
                case "$ne" -> FluentFilter.where(field).notEq(finalValue);
                case "$gt" ->
                    finalValue instanceof Comparable<?> c ? FluentFilter.where(field).gt(c) : Filter.ALL;
                case "$gte" ->
                    finalValue instanceof Comparable<?> c ? FluentFilter.where(field).gte(c) : Filter.ALL;
                case "$lt" ->
                    finalValue instanceof Comparable<?> c ? FluentFilter.where(field).lt(c) : Filter.ALL;
                case "$lte" ->
                    finalValue instanceof Comparable<?> c ? FluentFilter.where(field).lte(c) : Filter.ALL;
                case "$in" -> {
                    // Handle null or empty collection - return filter that matches nothing
                    if (finalValue == null) {
                        yield NONE;
                    }
                    // Handle collection parameter - value may be a List containing a placeholder
                    // that resolves to a Collection at runtime (e.g., WHERE id IN :ids)
                    List<Comparable<?>> resolvedValues = new ArrayList<>();
                    if (finalValue instanceof List<?> list && list.size() == 1) {
                        Object item = list.get(0);
                        // Check if it's a placeholder that resolves to a collection
                        Object resolved = resolveValue(item, params, namedParameters);
                        if (resolved instanceof Collection<?> coll) {
                            for (Object collItem : coll) {
                                Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(collItem));
                                if (itemResolved instanceof Comparable<?> c) {
                                    resolvedValues.add(c);
                                }
                            }
                        } else if (resolved != null && resolved.getClass().isArray()) {
                            // Handle array parameter
                            int len = java.lang.reflect.Array.getLength(resolved);
                            for (int i = 0; i < len; i++) {
                                Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(java.lang.reflect.Array.get(resolved, i)));
                                if (itemResolved instanceof Comparable<?> c) {
                                    resolvedValues.add(c);
                                }
                            }
                        } else if (resolved == null) {
                            // Null collection parameter - no matches
                            yield NONE;
                        } else if (resolved instanceof Comparable<?> c) {
                            resolvedValues.add(c);
                        }
                    } else if (finalValue instanceof Collection<?> coll) {
                        for (Object item : coll) {
                            Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(item, params, namedParameters)));
                            if (itemResolved instanceof Comparable<?> c) {
                                resolvedValues.add(c);
                            }
                        }
                    } else if (finalValue instanceof Object[] array) {
                        // Handle array parameter
                        for (Object item : array) {
                            Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(item, params, namedParameters)));
                            if (itemResolved instanceof Comparable<?> c) {
                                resolvedValues.add(c);
                            }
                        }
                    }
                    // Return NONE filter if no values (empty/null collection passed)
                    yield resolvedValues.isEmpty() ? NONE : FluentFilter.where(field).in(resolvedValues.toArray(new Comparable[0]));
                }
                case "$nin" -> {
                    // Handle null collection - return filter that matches everything
                    if (finalValue == null) {
                        yield Filter.ALL;
                    }
                    // Handle collection parameter - value may be a List containing a placeholder
                    List<Comparable<?>> resolvedValues = new ArrayList<>();
                    if (finalValue instanceof List<?> list && list.size() == 1) {
                        Object item = list.get(0);
                        // Check if it's a placeholder that resolves to a collection
                        Object resolved = resolveValue(item, params, namedParameters);
                        if (resolved instanceof Collection<?> coll) {
                            for (Object collItem : coll) {
                                Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(collItem));
                                if (itemResolved instanceof Comparable<?> c) {
                                    resolvedValues.add(c);
                                }
                            }
                        } else if (resolved instanceof Comparable<?> c) {
                            resolvedValues.add(c);
                        }
                    } else if (finalValue instanceof Collection<?> coll) {
                        for (Object item : coll) {
                            Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(item, params, namedParameters)));
                            if (itemResolved instanceof Comparable<?> c) {
                                resolvedValues.add(c);
                            }
                        }
                    } else if (finalValue instanceof Object[] array) {
                        // Handle array parameter
                        for (Object item : array) {
                            Object itemResolved = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(item, params, namedParameters)));
                            if (itemResolved instanceof Comparable<?> c) {
                                resolvedValues.add(c);
                            }
                        }
                    }
                    // Return ALL filter if no values (empty/null collection passed - NOT IN empty set matches all)
                    yield resolvedValues.isEmpty() ? Filter.ALL : FluentFilter.where(field).notIn(resolvedValues.toArray(new Comparable[0]));
                }
                case "$null" ->
                    Boolean.TRUE.equals(finalValue) ? FluentFilter.where(field).eq(null) : Filter.ALL;
                case "$notNull" ->
                    Boolean.TRUE.equals(finalValue) ? FluentFilter.where(field).notEq(null) : Filter.ALL;
                case "$between" -> {
                    if (finalValue instanceof List<?> list && list.size() == 2) {
                        Object v1 = entityMapper.toFilterValue(preConvertForFilter(resolveValue(list.get(0), params, namedParameters)));
                        Object v2 = entityMapper.toFilterValue(preConvertForFilter(resolveValue(list.get(1), params, namedParameters)));
                        yield FluentFilter.where(field).between((Comparable<?>) v1, (Comparable<?>) v2);
                    }
                    yield Filter.ALL;
                }
                case "$regex" -> {
                    // Resolve the regex pattern (may contain parameter placeholders)
                    Object resolved = resolveValue(finalValue, params, namedParameters);
                    String regexPattern = resolved != null ? resolved.toString() : "";
                    yield FluentFilter.where(field).regex(regexPattern);
                }
                case "$not" -> {
                    if (finalValue instanceof Map<?, ?> m) {
                        yield buildFieldFilter(entity, field, toStringObjectMap(m), params, namedParameters).not();
                    }
                    yield Filter.ALL;
                }
                case "$exists" ->
                    Boolean.TRUE.equals(finalValue) ? FluentFilter.where(field).notEq(null) : FluentFilter.where(field).eq(null);
                case "$empty" ->
                    Boolean.TRUE.equals(finalValue) ? Filter.or(FluentFilter.where(field).eq(""), FluentFilter.where(field).eq(null)) : Filter.and(FluentFilter.where(field).notEq(""), FluentFilter.where(field).notEq(null));
                case "$text" -> FluentFilter.where(field).text(finalValue != null ? finalValue.toString() : "");
                case "$near" -> {
                    if (value instanceof Map<?, ?> m) {
                        Object center = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(m.get("center"), params, namedParameters)));
                        Object distanceObj = resolveValue(m.get("distance"), params, namedParameters);
                        double distance = distanceObj instanceof Number n ? n.doubleValue() : 0.0;
                        Filter spatialFilter = createSpatialFilter(field, "near", new Class<?>[]{Object.class, double.class}, center, distance);
                        yield spatialFilter;
                    }
                    yield Filter.ALL;
                }
                case "$within" -> createSpatialFilter(field, finalValue, "within");
                case "$intersects" -> createSpatialFilter(field, finalValue, "intersects");
                default -> FluentFilter.where(field).eq(finalValue);
            };
            if (f != null && f != Filter.ALL) {
                fieldFilters.add(f);
            }
        }
        if (fieldFilters.isEmpty()) {
            return Filter.ALL;
        }
        return fieldFilters.size() == 1 ? fieldFilters.get(0) : Filter.and(fieldFilters.toArray(new Filter[0]));
    }

    private Filter createSpatialFilter(String field, String method, Class<?>[] argTypes, Object... args) {
        if (ClassUtils.isPresent(SPATIAL_FLUENT_FILTER_CLASS, null)) {
            try {
                Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                Method whereMethod = spatialClass.getMethod("where", String.class);
                Object spatialFluentFilter = whereMethod.invoke(null, field);
                
                // For $near, we need to extract Coordinate from Geometry
                if ("near".equals(method) && args.length == 2) {
                    Object center = args[0];
                    double distance = args[1] instanceof Number n ? n.doubleValue() : 0.0;
                    // Extract Coordinate from Geometry using reflection
                    Object coordinate = null;
                    if (center != null && ClassUtils.isPresent(GEOMETRY_CLASS, null)) {
                        Class<?> geometryClass = Class.forName(GEOMETRY_CLASS);
                        if (geometryClass.isInstance(center)) {
                            Method getCoordinateMethod = geometryClass.getMethod("getCoordinate");
                            coordinate = getCoordinateMethod.invoke(center);
                        }
                    }
                    if (coordinate != null) {
                        // Use Double (boxed) as per the actual method signature
                        Method filterMethod = spatialFluentFilter.getClass().getMethod("near", 
                            Class.forName("org.locationtech.jts.geom.Coordinate"), Double.class);
                        return (Filter) filterMethod.invoke(spatialFluentFilter, coordinate, Double.valueOf(distance));
                    }
                } else {
                    // For $within and $intersects, pass Geometry directly
                    Method filterMethod = spatialFluentFilter.getClass().getMethod(method, argTypes);
                    return (Filter) filterMethod.invoke(spatialFluentFilter, args);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create spatial filter for method: " + method, e);
            }
        }
        throw new IllegalStateException("Spatial filter '" + method + "' requested but 'nitrite-spatial' is not on the classpath.");
    }

    private Filter createSpatialFilter(String field, Object geometry, String method) {
        if (geometry == null) {
            return Filter.ALL;
        }
        // Check if the value is a Geometry type using reflection
        if (!ClassUtils.isPresent(GEOMETRY_CLASS, null)) {
            throw new IllegalStateException("Spatial filter '" + method + "' requires 'nitrite-spatial' on the classpath.");
        }
        try {
            Class<?> geometryClass = Class.forName(GEOMETRY_CLASS);
            if (geometryClass.isInstance(geometry)) {
                Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                Method whereMethod = spatialClass.getMethod("where", String.class);
                Object spatialFluentFilter = whereMethod.invoke(null, field);
                // Use Geometry.class as per the actual method signature
                Method filterMethod = spatialFluentFilter.getClass().getMethod(method, geometryClass);
                Filter result = (Filter) filterMethod.invoke(spatialFluentFilter, geometry);
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create spatial filter for method: " + method, e);
        }
        return Filter.ALL;
    }
}
