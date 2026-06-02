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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared JSON parameter-binding utilities used by both
 * {@link DefaultNitriteRepositoryOperations} and {@link NitriteQueryExecutor}.
 */
@Internal
final class NitriteQueryBinder {

    private NitriteQueryBinder() {}

    /**
     * Extracts the numeric placeholder index from a {@code "$mn_qp:N"} string or
     * a {@code {"$mn_qp": N}} map. Returns {@code null} if the value is not a placeholder.
     */
    static Integer extractPlaceholderIndex(Object value) {
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
    static Object resolveParameterValue(Object value, Object[] jsonParams,
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
                for (Object opVal : m.values()) {
                    Integer idx = extractPlaceholderIndex(opVal);
                    if (idx != null) max = Math.max(max, idx);
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
    static Map<String, Object> buildNamedParameterValues(PreparedQuery<?, ?> q,
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
}
