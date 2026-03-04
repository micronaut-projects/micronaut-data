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
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

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
     * @param filterObj       the filter object
     * @param params          positional parameters
     * @param namedParameters named parameters
     * @return the Nitrite Filter
     */
    public Filter buildFilterFromJson(
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
                            Filter f = buildFilterFromJson(toStringObjectMap(m), params, namedParameters);
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
                            Filter f = buildFilterFromJson(toStringObjectMap(m), params, namedParameters);
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
                    Filter f = buildFieldFilter(key, toStringObjectMap(m), params, namedParameters);
                    if (f != null && f != Filter.ALL) {
                        filters.add(f);
                    }
                } else {
                    Filter f = buildFieldFilter(key, Collections.singletonMap("$eq", resolvedValue), params, namedParameters);
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
     * Resolve a parameter placeholder to its actual value.
     *
     * @param value           the potential placeholder
     * @param params          positional parameters
     * @param namedParameters named parameters
     * @return the resolved value
     */
    private Object resolveValue(Object value, Object[] params, Map<String, Object> namedParameters) {
        if (value instanceof String s) {
            if (s.startsWith("$mn_qp:")) {
                try {
                    int idx = Integer.parseInt(s.substring(7));
                    if (params != null && idx >= 0 && idx < params.length) {
                        return params[idx];
                    }
                } catch (Exception ignored) {
                    // ignore parse exception
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
     * Build a Nitrite Filter for a specific field.
     *
     * @param field           the field name
     * @param operators       the operator map
     * @param params          positional parameters
     * @param namedParameters named parameters
     * @return the Nitrite Filter
     */
    public Filter buildFieldFilter(
        final String field,
        final Map<String, Object> operators,
        final Object[] params,
        final Map<String, Object> namedParameters) {
        List<Filter> fieldFilters = new ArrayList<>();
        for (Map.Entry<String, Object> opEntry : operators.entrySet()) {
            String op = opEntry.getKey();
            Object value = resolveValue(opEntry.getValue(), params, namedParameters);
            Object coercedValue = maybeCoerceUuid(field, value);
            Object finalValue = entityMapper.toNitriteFilterValue(coercedValue);
            Filter f = switch (op) {
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
                        yield FluentFilter.where(field).between((Comparable<?>) v1, (Comparable<?>) v2);
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
}
