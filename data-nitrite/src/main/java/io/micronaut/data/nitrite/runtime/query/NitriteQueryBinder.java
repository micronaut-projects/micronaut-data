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
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.nitrite.runtime.DefaultNitriteRepositoryOperations;
import io.micronaut.data.nitrite.runtime.NameUtils;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.read.NitriteQueryExecutor;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;

import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared JSON parameter-binding utilities used by both
 * {@link DefaultNitriteRepositoryOperations} and {@link NitriteQueryExecutor}.
 */
@Internal
public final class NitriteQueryBinder {

    private final NitriteEntityMapper entityMapper;
    private final Nitrite database;

    public NitriteQueryBinder(NitriteEntityMapper entityMapper, Nitrite database) {
        this.entityMapper = entityMapper;
        this.database = database;
    }

    /**
     * Extracts the numeric placeholder index from a {@code "$mn_qp:N"} string or
     * a {@code {"$mn_qp": N}} map. Returns {@code null} if the value is not a placeholder.
     */
    public static Integer extractPlaceholderIndex(Object value) {
        if (value instanceof String s && s.startsWith("$mn_qp:")) {
            try {
                return Integer.parseInt(s.substring(7));
            } catch (NumberFormatException ignored) {
            }
        }
        if (value instanceof Map<?, ?> vm && vm.size() == 1 && vm.get("$mn_qp") instanceof Integer idx) {
            return idx;
        }
        return null;
    }

    /**
     * Resolves a raw filter or update value: replaces {@code "$mn_qp:N"} placeholders and
     * {@code ":"}-prefixed named parameters with their actual values, converting through
     * {@code toFilterValue}. Returns the value unchanged if it is not a placeholder.
     */
    @SuppressWarnings({"rawtypes"})
    public static Object resolveParameterValue(Object value, Object[] jsonParams,
                                        Map<String, Object> namedParameters,
                                        Function<Object, Object> toFilterValue) {
        if (value instanceof String s) {
            Object resolved = null;
            boolean isPlaceholder = false;
            if (s.startsWith("$mn_qp:")) {
                isPlaceholder = true;
                try {
                    int idx = Integer.parseInt(s.substring(7));
                    if (jsonParams != null && idx >= 0 && idx < jsonParams.length) {
                        resolved = jsonParams[idx];
                    }
                } catch (Exception ignored) {
                    // Ignore parsing errors for malformed placeholders
                }
            } else if (s.startsWith(":")) {
                isPlaceholder = true;
                String pname = s.substring(1);
                if (namedParameters.containsKey(pname)) {
                    resolved = namedParameters.get(pname);
                }
            }
            if (isPlaceholder) {
                return toFilterValue.apply(resolved);
            }
        }
        if (value instanceof Map vm && vm.get("$mn_qp") instanceof Integer idx
                && idx >= 0 && jsonParams != null && idx < jsonParams.length) {
            return toFilterValue.apply(jsonParams[idx]);
        }
        return value;
    }

    /**
     * Ensures the JSON params array is large enough to hold all {@code $mn_qp:N} placeholder
     * indices found in the filter map, then calls {@code fillMissing} to populate any null slots.
     */
    static Object[] ensureJsonParamsForFilter(Map<String, Object> filterMap, Object[] jsonParams,
                                              Consumer<Object[]> fillMissing) {
        int maxIdx = findMaxPlaceholderIndex(filterMap);
        if (maxIdx < 0) {
            return jsonParams;
        }
        Object[] out = jsonParams == null ? new Object[0] : jsonParams;
        if (out.length <= maxIdx) {
            out = Arrays.copyOf(out, maxIdx + 1);
        }
        fillMissing.accept(out);
        return out;
    }

    /**
     * Returns the highest {@code $mn_qp:N} index in a filter map, or -1.
     * Scans one level deep into operator maps ({@code {"$eq": "$mn_qp:0"}}),
     * which matches the structure produced by the Nitrite query builder.
     */
    private static int findMaxPlaceholderIndex(Map<String, Object> filterMap) {
        int max = -1;
        for (Object value : filterMap.values()) {
            if (value instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> opEntry : m.entrySet()) {
                    Object opVal = opEntry.getValue();
                    Integer idx = extractPlaceholderIndex(opVal);
                    if (idx != null) {
                        max = Math.max(max, idx);
                    } else if (opVal instanceof Map<?, ?> nestedMap) {
                        // handles $near: {center: {$mn_qp:0}, distance: {$mn_qp:1}}
                        for (Object nestedVal : nestedMap.values()) {
                            idx = extractPlaceholderIndex(nestedVal);
                            if (idx != null) max = Math.max(max, idx);
                        }
                    }
                }
            } else {
                Integer idx = extractPlaceholderIndex(value);
                if (idx != null) max = Math.max(max, idx);
            }
        }
        return max;
    }

    /**
     * Builds a named-parameter map from query bindings and method argument names.
     * Values are converted via {@code toFilterValue} before being stored.
     */
    public static Map<String, Object> buildNamedParameterValues(PreparedQuery<?, ?> q,
                                                          Function<Object, Object> toFilterValue) {
        Object[] params = q.getParameterArray();
        if (params == null || params.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>();
        List<QueryParameterBinding> bindings = q.getQueryBindings();
        if (bindings != null) {
            for (QueryParameterBinding b : bindings) {
                if (b.getName() != null && b.getParameterIndex() >= 0 && b.getParameterIndex() < params.length) {
                    result.put(b.getName(), toFilterValue.apply(params[b.getParameterIndex()]));
                }
            }
        }
        Argument[] args = q.getArguments();
        if (args != null) {
            int len = Math.min(args.length, params.length);
            for (int i = 0; i < len; i++) {
                if (!args[i].getName().isEmpty()) {
                    result.putIfAbsent(args[i].getName(), toFilterValue.apply(params[i]));
                }
            }
        }
        return result;
    }

    // ─── Instance methods: PreparedQuery parameter resolution ─────────────────────

    public Object[] buildJsonParameterValues(@NonNull PreparedQuery<?, ?> q) {
        return buildJsonParameterValues(q, this::toFilterValue, this::readSegmentValue);
    }

    /**
     * Builds an array of JSON parameter values from a prepared query.
     *
     * @param q the prepared query
     * @param toFilterValue the value mapper function
     * @param readSegmentValue the segment reader function (optional)
     * @return the array of JSON parameter values
     */
    public static Object[] buildJsonParameterValues(@NonNull PreparedQuery<?, ?> q,
                                              @NonNull Function<Object, Object> toFilterValue,
                                              @Nullable BiFunction<Object, String, Object> readSegmentValue) {
        Object[] methodParams = q.getParameterArray();
        List<QueryParameterBinding> bindings = q.getQueryBindings();
        if (bindings == null || bindings.isEmpty()) {
            return methodParams;
        }
        return bindings.stream().map(binding -> resolveJsonBindingValue(binding, methodParams, toFilterValue, readSegmentValue)).toArray();
    }

    public Object[] ensureJsonParamsForFilter(@NonNull Map<String, Object> filterMap,
                                       @NonNull Object[] methodParams,
                                       @Nullable Object[] jsonParams) {
        return NitriteQueryBinder.ensureJsonParamsForFilter(
            filterMap, jsonParams, out -> fillMissingParamsFromFilter(filterMap, methodParams, out));
    }

    private static Object resolveJsonBindingValue(@NonNull QueryParameterBinding binding,
                                                  @Nullable Object[] methodParams,
                                                  @NonNull Function<Object, Object> toFilterValue,
                                                  @Nullable BiFunction<Object, String, Object> readSegmentValue) {
        Object bindingValue = binding.getValue();
        if (bindingValue != null && !(bindingValue instanceof BindingParameter)) {
            return toFilterValue.apply(bindingValue);
        }
        int idx = binding.getParameterIndex();
        Object base = (methodParams != null && idx >= 0 && idx < methodParams.length) ? methodParams[idx] : null;
        String[] parameterBindingPath = binding.getParameterBindingPath();
        String[] path = parameterBindingPath != null ? parameterBindingPath : binding.getPropertyPath();
        if (path == null || path.length == 0 || isDirectBindableScalar(base)
                || base instanceof Collection || (base != null && base.getClass().isArray())) {
            return toFilterValue.apply(base);
        }
        if (readSegmentValue == null) {
            return toFilterValue.apply(base);
        }
        Object current = base;
        for (String segment : path) {
            if (current == null) break;
            current = readSegmentValue.apply(current, segment);
        }
        return toFilterValue.apply(current);
    }

    private static boolean isDirectBindableScalar(@Nullable Object value) {
        return switch (value) {
            case CharSequence ignored -> true;
            case Number ignored -> true;
            case Boolean ignored -> true;
            case Character ignored -> true;
            case Enum<?> ignored -> true;
            case UUID ignored -> true;
            case Temporal ignored -> true;
            case Date ignored -> true;
            case null, default -> false;
        };
    }

    private void fillMissingParamsFromFilter(Map<String, Object> filterMap, Object[] methodParams, Object[] out) {
        for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
            Object value = entry.getValue();
            if (("$and".equals(entry.getKey()) || "$or".equals(entry.getKey())) && value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> im) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> sub = (Map<String, Object>) im;
                        fillMissingParamsFromFilter(sub, methodParams, out);
                    }
                }
                continue;
            }
            if (value instanceof Map<?, ?> ops) {
                for (Object opVal : ops.values()) {
                    Integer idx = extractPlaceholderIndex(opVal);
                    if (idx != null && idx >= 0 && idx < out.length && out[idx] == null) {
                        out[idx] = extractPropertyFromSingleArg(methodParams, entry.getKey());
                    } else if (opVal instanceof Map<?, ?> nestedMap) {
                        for (Object nestedVal : nestedMap.values()) {
                            Integer nestedIdx = extractPlaceholderIndex(nestedVal);
                            if (nestedIdx != null && nestedIdx >= 0 && nestedIdx < out.length && out[nestedIdx] == null
                                    && methodParams != null && nestedIdx < methodParams.length) {
                                out[nestedIdx] = toFilterValue(methodParams[nestedIdx]);
                            }
                        }
                    }
                }
            } else {
                Integer idx = extractPlaceholderIndex(value);
                if (idx != null && idx >= 0 && idx < out.length && out[idx] == null) {
                    out[idx] = extractPropertyFromSingleArg(methodParams, entry.getKey());
                }
            }
        }
    }

    private Object extractPropertyFromSingleArg(@Nullable Object[] methodParams, String property) {
        if (methodParams == null || methodParams.length != 1 || methodParams[0] == null) {
            return null;
        }
        Object arg = methodParams[0];
        if ("id".equals(property) || "_id".equals(property)) {
            return entityMapper.toNitriteFilterValue(arg);
        }
        String[] parts = property.split("\\.");
        for (int start = 0; start < parts.length; start++) {
            Object current = arg;
            for (int i = start; i < parts.length; i++) {
                current = readSegmentValue(current, parts[i]);
                if (current == null) break;
            }
            if (current != null) {
                return entityMapper.toNitriteFilterValue(current);
            }
        }
        return entityMapper.toNitriteFilterValue(arg);
    }

    @Nullable
    private Object readSegmentValue(@Nullable Object current, String segment) {
        if (current == null) return null;
        String alt = NameUtils.snakeToCamel(segment);
        return switch (current) {
            case Document d -> {
                Object v = d.get(segment);
                yield v != null || segment.equals(alt) ? v : d.get(alt);
            }
            case Map<?, ?> m -> {
                Object v = m.get(segment);
                yield v != null || segment.equals(alt) ? v : m.get(alt);
            }
            default -> tryConvertAndRead(current, segment, alt);
        };
    }

    @Nullable
    private Object tryConvertAndRead(Object current, String segment, String alt) {
        try {
            Document doc = (Document) database.getConfig().nitriteMapper().tryConvert(current, Document.class);
            Object v = doc.get(segment);
            return v != null || segment.equals(alt) ? v : doc.get(alt);
        } catch (Exception ignored) {
            // If conversion or field access fails, return null as fallback
            return null;
        }
    }

    private Object toFilterValue(Object value) {
        return entityMapper.toFilterValue(value);
    }
}
