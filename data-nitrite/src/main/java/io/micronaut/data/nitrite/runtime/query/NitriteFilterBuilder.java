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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(NitriteFilterBuilder.class);
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
        Filter result = filters.size() == 1 ? filters.get(0) : Filter.and(filters.toArray(new Filter[0]));
        if (LOG.isDebugEnabled()) {
            LOG.debug("buildFilterFromJson: result filter={}", result);
        }
        return result;
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
            } catch (Exception ignored) {
            }
        }
        return value;
    }

    private Object preConvertForFilter(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        if (value instanceof LocalTime localTime) {
            return localTime.toString();
        }
        return value;
    }

    public Filter buildFieldFilter(
        final RuntimePersistentEntity<?> entity,
        final String rawField,
        final Map<String, Object> operators,
        final Object[] params,
        final Map<String, Object> namedParameters) {

        if (rawField.contains(".")) {
            return buildNestedFilter(entity, rawField, operators, params, namedParameters);
        }

        String field = entityMapper.normalizeFieldName(rawField, entity);

        if (entity != null) {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            if (identity != null && identity.isAnnotationPresent(EmbeddedId.class) &&
                (identity.getName().equals(field) || "id".equals(field) || "_id".equals(field))) {

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

        if (prop instanceof io.micronaut.data.model.runtime.RuntimeAssociation<?> assoc) {
            io.micronaut.data.annotation.Relation.Kind kind = assoc.getKind();
            boolean isCollection = kind == io.micronaut.data.annotation.Relation.Kind.ONE_TO_MANY ||
                                 kind == io.micronaut.data.annotation.Relation.Kind.MANY_TO_MANY;

            RuntimePersistentEntity<?> associatedEntity = assoc.getAssociatedEntity();
            if (isCollection) {
                Filter subFilter = buildFieldFilter(associatedEntity, remaining, operators, params, namedParameters);
                return FluentFilter.where(fieldName).elemMatch(subFilter);
            } else {
                return buildOperatorFiltersForPath(entity, fieldName + "." + remaining, operators, params, namedParameters);
            }
        }

        // Fallback to dot notation
        return buildOperatorFiltersForPath(entity, fieldPath, operators, params, namedParameters);
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

    private Filter buildOperatorFilter(
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
                yield FluentFilter.where(field).regex(resolved != null ? resolved.toString() : "");
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
            return createSpatialFilter(field, "near", new Class<?>[]{Object.class, double.class}, center, distance);
        }
        return Filter.ALL;
    }

    private Filter createSpatialFilter(String field, String method, Class<?>[] argTypes, Object... args) {
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
                    if (coordinate != null) {
                        Method filterMethod = spatialFluentFilter.getClass().getMethod("near", Class.forName("org.locationtech.jts.geom.Coordinate"), Double.class);
                        return (Filter) filterMethod.invoke(spatialFluentFilter, coordinate, Double.valueOf(distance));
                    }
                } else {
                    Method filterMethod = spatialFluentFilter.getClass().getMethod(method, argTypes);
                    return (Filter) filterMethod.invoke(spatialFluentFilter, args);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create spatial filter for method: " + method, e);
            }
        }
        return Filter.ALL;
    }

    private Filter createSpatialFilter(String field, Object geometry, String method) {
        if (geometry == null || !ClassUtils.isPresent(GEOMETRY_CLASS, null)) {
            return Filter.ALL;
        }
        try {
            Class<?> geometryClass = Class.forName(GEOMETRY_CLASS);
            if (geometryClass.isInstance(geometry)) {
                Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                Method whereMethod = spatialClass.getMethod("where", String.class);
                Object spatialFluentFilter = whereMethod.invoke(null, field);
                Method filterMethod = spatialFluentFilter.getClass().getMethod(method, geometryClass);
                return (Filter) filterMethod.invoke(spatialFluentFilter, geometry);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create spatial filter for method: " + method, e);
        }
        return Filter.ALL;
    }
}
