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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builder for Nitrite Filters from JSON-like structures.
 *
 * @since 1.0.0
 */
@Internal
public final class NitriteFilterBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteFilterBuilder.class);

    private static final String SPATIAL_FLUENT_FILTER_CLASS = "org.dizitart.no2.spatial.SpatialFluentFilter";
    private static final String GEOMETRY_CLASS = "org.locationtech.jts.geom.Geometry";

    private final NitriteEntityMapper entityMapper;

    /**
     * Create a new filter builder.
     *
     * @param entityMapper the entity mapper
     */
    public NitriteFilterBuilder(@NonNull NitriteEntityMapper entityMapper) {
        this.entityMapper = entityMapper;
    }

    /**
     * Build a Nitrite Filter from a Map structure.
     *
     * @param filterObj       the filter object
     * @param params          positional parameters
     * @param namedParameters named parameters
     * @return the Nitrite Filter
     */
    @NonNull
    public Filter buildFilterFromJson(
        @Nullable final Map<String, Object> filterObj,
        @Nullable final Object[] params,
        @NonNull final Map<String, Object> namedParameters) {
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
            if ("$and".equals(key)) {
                if (value instanceof List<?> list) {
                    List<Filter> andFilters = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Filter f = buildFilterFromJson(toStringObjectMap(m), params, namedParameters);
                            if (f != Filter.ALL) {
                                andFilters.add(f);
                            }
                        }
                    }
                    if (!andFilters.isEmpty()) {
                        filters.add(Filter.and(andFilters.toArray(new Filter[0])));
                    }
                }
            } else if ("$or".equals(key)) {
                if (value instanceof List<?> list) {
                    List<Filter> orFilters = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Filter f = buildFilterFromJson(toStringObjectMap(m), params, namedParameters);
                            if (f != Filter.ALL) {
                                orFilters.add(f);
                            }
                        }
                    }
                    if (!orFilters.isEmpty()) {
                        filters.add(Filter.or(orFilters.toArray(new Filter[0])));
                    }
                }
            } else if (key != null) {
                Object resolvedValue = resolveValue(value, params, namedParameters);
                if (resolvedValue instanceof Map<?, ?> m && !isPlaceholder(m)) {
                    Filter f = buildFieldFilter(key, toStringObjectMap(m), params, namedParameters);
                    if (f != Filter.ALL) {
                        filters.add(f);
                    }
                } else {
                    Filter f = buildFieldFilter(key, Collections.singletonMap("$eq", resolvedValue), params, namedParameters);
                    if (f != Filter.ALL) {
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

    private boolean isPlaceholder(@Nullable Object value) {
        if (value instanceof String s && (s.startsWith("$mn_qp:") || s.startsWith(":"))) {
            return true;
        }
        return value instanceof Map<?, ?> vm && vm.size() == 1 && vm.containsKey("$mn_qp");
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private Map<String, Object> toStringObjectMap(@NonNull Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @Nullable
    private Object resolveValue(@Nullable Object value, @Nullable Object[] params, @NonNull Map<String, Object> namedParameters) {
        if (value instanceof String s) {
            if (s.startsWith("$mn_qp:")) {
                try {
                    int idx = Integer.parseInt(s.substring(7));
                    if (params != null && idx >= 0 && idx < params.length) {
                        return params[idx];
                    }
                } catch (Exception ignored) {
                    // ignore invalid JSON parameter index format
                }
            } else if (s.startsWith(":")) {
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

    @Nullable
    private Object maybeCoerceUuid(@NonNull String field, @Nullable Object value) {
        if (value instanceof String s && ("id".equals(field) || "_id".equals(field))) {
            try {
                return UUID.fromString(s);
            } catch (Exception ignored) {
                // use literal value if UUID conversion fails
            }
        }
        return value;
    }

    /**
     * Builds a field filter.
     *
     * @param field the field name
     * @param operators the filter map
     * @param params the query parameters
     * @param namedParameters the named parameters
     * @return the nitrite filter
     */
    @NonNull
    public Filter buildFieldFilter(
        @NonNull final String field,
        @NonNull final Map<String, Object> operators,
        @Nullable final Object[] params,
        @NonNull final Map<String, Object> namedParameters) {
        List<Filter> fieldFilters = new ArrayList<>();
        for (Map.Entry<String, Object> opEntry : operators.entrySet()) {
            String op = opEntry.getKey();
            Object value = resolveValue(opEntry.getValue(), params, namedParameters);
            Object coercedValue = maybeCoerceUuid(field, value);
            Object finalValue = entityMapper.toNitriteFilterValue(coercedValue);
            Filter f = switch (op != null ? op : "$eq") {
                case "$eq" -> entityMapper.eqWithNumericCoercion(field, finalValue);
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
                    if (finalValue instanceof Collection<?> coll) {
                        List<Comparable<?>> resolvedValues = new ArrayList<>();
                        for (Object item : coll) {
                            Object resolved = entityMapper.toNitriteFilterValue(resolveValue(item, params, namedParameters));
                            if (resolved instanceof Comparable<?> c) {
                                resolvedValues.add(c);
                            }
                        }
                        yield FluentFilter.where(field).in(resolvedValues.toArray(new Comparable[0]));
                    }
                    yield FluentFilter.where(field).eq(finalValue);
                }
                case "$nin" -> {
                    if (finalValue instanceof Collection<?> coll) {
                        List<Comparable<?>> resolvedValues = new ArrayList<>();
                        for (Object item : coll) {
                            Object resolved = entityMapper.toNitriteFilterValue(resolveValue(item, params, namedParameters));
                            if (resolved instanceof Comparable<?> c) {
                                resolvedValues.add(c);
                            }
                        }
                        yield FluentFilter.where(field).notIn(resolvedValues.toArray(new Comparable[0]));
                    }
                    yield FluentFilter.where(field).notEq(finalValue);
                }
                case "$null" ->
                    Boolean.TRUE.equals(finalValue) ? FluentFilter.where(field).eq(null) : Filter.ALL;
                case "$notNull" ->
                    Boolean.TRUE.equals(finalValue) ? FluentFilter.where(field).notEq(null) : Filter.ALL;
                case "$between" -> {
                    if (finalValue instanceof List<?> list && list.size() == 2) {
                        Object v1 = entityMapper.toFilterValue(resolveValue(list.get(0), params, namedParameters));
                        Object v2 = entityMapper.toFilterValue(resolveValue(list.get(1), params, namedParameters));
                        if (v1 instanceof Comparable<?> c1 && v2 instanceof Comparable<?> c2) {
                            yield FluentFilter.where(field).between(c1, c2);
                        }
                    }
                    yield Filter.ALL;
                }
                case "$regex" -> FluentFilter.where(field).regex(finalValue != null ? finalValue.toString() : "");
                case "$not" -> {
                    if (finalValue instanceof Map<?, ?> m) {
                        yield buildFieldFilter(field, toStringObjectMap(m), params, namedParameters).not();
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
                        Object center = entityMapper.toNitriteFilterValue(resolveValue(m.get("center"), params, namedParameters));
                        Object distanceObj = resolveValue(m.get("distance"), params, namedParameters);
                        if (center != null) {
                            double distance = distanceObj instanceof Number n ? n.doubleValue() : 0.0;
                            LOG.debug("Building $near filter: field={}, center={} (class={}), distance={}", field, center,
                                center.getClass().getName(), distance);
                            Filter spatialFilter = createSpatialFilter(field, "near", new Class<?>[]{Object.class, double.class}, center, distance);
                            LOG.debug("Created $near filter: {}", spatialFilter);
                            yield spatialFilter;
                        }
                    }
                    yield Filter.ALL;
                }
                case "$within" -> createSpatialFilter(field, finalValue, "within");
                case "$intersects" -> createSpatialFilter(field, finalValue, "intersects");
                default -> FluentFilter.where(field).eq(finalValue);
            };
            if (f != Filter.ALL) {
                fieldFilters.add(f);
            }
        }
        if (fieldFilters.isEmpty()) {
            return Filter.ALL;
        }
        return fieldFilters.size() == 1 ? fieldFilters.get(0) : Filter.and(fieldFilters.toArray(new Filter[0]));
    }

    @NonNull
    private Filter createSpatialFilter(@NonNull String field, @NonNull String method, @NonNull Class<?>[] argTypes, @NonNull Object... args) {
        if (ClassUtils.isPresent(SPATIAL_FLUENT_FILTER_CLASS, null)) {
            try {
                Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                Method whereMethod = spatialClass.getMethod("where", String.class);
                Object spatialFluentFilter = whereMethod.invoke(null, field);
                
                if ("near".equals(method) && args.length == 2) {
                    Object center = args[0];
                    double distance = args[1] instanceof Number n ? n.doubleValue() : 0.0;
                    Object coordinate = null;
                    if (center != null && ClassUtils.isPresent(GEOMETRY_CLASS, null)) {
                        Class<?> geometryClass = Class.forName(GEOMETRY_CLASS);
                        if (geometryClass.isInstance(center)) {
                            Method getCoordinateMethod = geometryClass.getMethod("getCoordinate");
                            coordinate = getCoordinateMethod.invoke(center);
                        }
                    }
                    if (coordinate != null && spatialFluentFilter != null) {
                        Method filterMethod = spatialFluentFilter.getClass().getMethod("near", 
                            Class.forName("org.locationtech.jts.geom.Coordinate"), Double.class);
                        return Objects.requireNonNull((Filter) filterMethod.invoke(spatialFluentFilter, coordinate, Double.valueOf(distance)));
                    }
                } else if (spatialFluentFilter != null) {
                    Method filterMethod = spatialFluentFilter.getClass().getMethod(method, argTypes);
                    return Objects.requireNonNull((Filter) filterMethod.invoke(spatialFluentFilter, args));
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create spatial filter for method: " + method, e);
            }
        }
        throw new IllegalStateException("Spatial filter '" + method + "' requested but 'nitrite-spatial' is not on the classpath.");
    }

    @NonNull
    private Filter createSpatialFilter(@NonNull String field, @Nullable Object geometry, @NonNull String method) {
        if (geometry == null) {
            return Filter.ALL;
        }
        if (!ClassUtils.isPresent(GEOMETRY_CLASS, null)) {
            throw new IllegalStateException("Spatial filter '" + method + "' requires 'nitrite-spatial' on the classpath.");
        }
        try {
            Class<?> geometryClass = Class.forName(GEOMETRY_CLASS);
            if (geometryClass.isInstance(geometry)) {
                LOG.debug("Building ${} filter: field={}, geometry={} (class={})", method, field, geometry, geometry.getClass().getName());
                Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                Method whereMethod = spatialClass.getMethod("where", String.class);
                Object spatialFluentFilter = whereMethod.invoke(null, field);
                if (spatialFluentFilter != null) {
                    Method filterMethod = spatialFluentFilter.getClass().getMethod(method, geometryClass);
                    Filter result = (Filter) filterMethod.invoke(spatialFluentFilter, geometry);
                    LOG.debug("Created ${} filter: {}", method, result);
                    return Objects.requireNonNull(result);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create spatial filter for method: " + method, e);
        }
        return Filter.ALL;
    }
}
