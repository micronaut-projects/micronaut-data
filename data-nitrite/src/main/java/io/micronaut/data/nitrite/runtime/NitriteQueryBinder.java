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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Shared SQL/JSON parameter-binding utilities used by both
 * {@link DefaultNitriteRepositoryOperations} and {@link NitriteQueryExecutor}.
 *
 * <p>All methods are static. SQL regex constants are package-private so callers in the same
 * package reference them directly without a getter call.</p>
 */
@Internal
final class NitriteQueryBinder {

    static final Pattern SQL_COMPARISON =
        Pattern.compile("(?:\\w+\\.)?(\\w+)\\s*(=|!=|<>|>|<|>=|<=)\\s*:(\\w+)");
    static final Pattern SQL_IN_CLAUSE =
        Pattern.compile("(?:\\w+\\.)?(\\w+)\\s+(NOT\\s+)?IN\\s*\\(\\s*:(\\w+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    static final Pattern SQL_IS_NOT_NULL =
        Pattern.compile("(?:\\w+\\.)?(\\w+)\\s+IS\\s+NOT\\s+NULL");
    static final Pattern SQL_IS_NULL =
        Pattern.compile("(?:\\w+\\.)?(\\w+)\\s+IS\\s+(?!NOT\\s+)NULL");

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
     * Resolves a {@code :pN} positional parameter name to the corresponding method argument.
     * Returns {@code null} if the name is not positional or the index is out of range.
     */
    static Object resolveParam(String pname, Object[] params) {
        try {
            if (pname.startsWith("p")) {
                int idx = Integer.parseInt(pname.substring(1)) - 1;
                if (params != null && idx >= 0 && idx < params.length) {
                    return params[idx];
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    /**
     * Resolves a SQL parameter name: checks named parameters first, then falls back to
     * positional {@code :pN} resolution via {@link #resolveParam}.
     */
    static Object resolveSqlParam(String pname, Object[] params, Map<String, Object> namedParameters) {
        if (namedParameters != null && namedParameters.containsKey(pname)) {
            return namedParameters.get(pname);
        }
        return resolveParam(pname, params);
    }

    /**
     * Reorders method parameters into SQL positional order using {@code QueryParameterBinding}
     * name hints (e.g. {@code "p1"}, {@code "p2"}).
     */
    static Object[] reorderParamsForSql(PreparedQuery<?, ?> q) {
        Object[] raw = q.getParameterArray();
        List<QueryParameterBinding> bindings = q.getQueryBindings();
        if (bindings == null || bindings.isEmpty() || raw == null) {
            return raw;
        }
        Object[] reordered = new Object[bindings.size()];
        for (QueryParameterBinding b : bindings) {
            if (b.getName() != null && b.getName().startsWith("p")) {
                try {
                    int pos = Integer.parseInt(b.getName().substring(1)) - 1;
                    if (pos >= 0 && pos < reordered.length && b.getParameterIndex() >= 0 && b.getParameterIndex() < raw.length) {
                        reordered[pos] = raw[b.getParameterIndex()];
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return reordered;
    }

    /**
     * Resolves a raw filter or update value: replaces {@code "$mn_qp:N"} placeholders and
     * {@code ":"}-prefixed named parameters with their actual values, converting through
     * {@code toFilterValue}. Returns the value unchanged if it is not a placeholder.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
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
