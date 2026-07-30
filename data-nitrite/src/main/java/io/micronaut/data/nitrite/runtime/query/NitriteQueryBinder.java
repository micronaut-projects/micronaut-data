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
import org.dizitart.no2.collection.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.AND;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.OR;

/**
 * Shared JSON parameter-binding utilities used by both
 * {@link DefaultNitriteRepositoryOperations} and {@link NitriteQueryExecutor}.
 */
@Internal
public final class NitriteQueryBinder {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteQueryBinder.class);

    private final NitriteEntityMapper entityMapper;

    /**
     * Creates a new query binder for Nitrite queries.
     * @param entityMapper the entity mapper used to convert parameter values
     */
    public NitriteQueryBinder(NitriteEntityMapper entityMapper) {
        this.entityMapper = entityMapper;
    }

    /**
     * Extracts the numeric placeholder index from a {@code "$mn_qp:N"} string or
     * a {@code {"$mn_qp": N}} map. Returns {@code null} if the value is not a placeholder.
     *
     * @param value the raw value to inspect
     * @return the placeholder index, or {@code null} if the value is not a placeholder
     */
    public static @Nullable Integer extractPlaceholderIndex(@Nullable Object value) {
        if (value instanceof String s && s.startsWith("$mn_qp:")) {
            try {
                return Integer.parseInt(s.substring(7));
            } catch (NumberFormatException ignored) {
                // Fall back if the suffix is not a valid integer placeholder
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
     *
     * @param value the raw value
     * @param jsonParams positional JSON parameters
     * @param namedParameters named parameters
     * @param toFilterValue mapper used to normalize resolved values
     * @return the resolved value
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
     *
     * @param filterMap the filter map
     * @param jsonParams the existing JSON parameters
     * @param fillMissing callback that fills unresolved parameter slots
     * @return the expanded JSON parameter array
     */
    static Object[] ensureJsonParamsForFilter(@Nullable Map<String, Object> filterMap,
                                              @Nullable Object[] jsonParams,
                                              Consumer<Object[]> fillMissing) {
        int maxIdx = findMaxPlaceholderIndex(filterMap);
        if (maxIdx < 0) {
            return jsonParams == null ? new Object[0] : jsonParams;
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
    private static int findMaxPlaceholderIndex(@Nullable Map<String, Object> filterMap) {
        if (filterMap == null) {
            return -1;
        }
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
                            if (idx != null) {
                                max = Math.max(max, idx);
                            }
                        }
                    }
                }
            } else {
                Integer idx = extractPlaceholderIndex(value);
                if (idx != null) {
                    max = Math.max(max, idx);
                }
            }
        }
        return max;
    }

    /**
     * Builds a named-parameter map from query bindings and method argument names.
     * Values are converted via {@code toFilterValue} before being stored.
     *
     * @param q the prepared query
     * @param toFilterValue mapper used to normalize values
     * @return the named parameter map
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
        return Collections.unmodifiableMap(result);
    }

    // ─── Instance methods: PreparedQuery parameter resolution ─────────────────────

    /**
     * Builds the positional JSON parameter values array from a prepared query.
     * @param q the prepared query containing the arguments
     * @return an array of resolved and converted JSON parameter values
     */
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

    /**
     * Ensures that the positional JSON parameters array contains the required values for the given filter map.
     * @param filterMap the JSON filter map
     * @param methodParams the original method parameters
     * @param jsonParams the current JSON parameters array, if any
     * @return a complete array of JSON parameter values required by the filter
     */
    public Object[] ensureJsonParamsForFilter(@Nullable Map<String, Object> filterMap,
                                              @NonNull Object[] methodParams,
                                              @Nullable Object[] jsonParams) {
        if (filterMap == null) {
            return methodParams;
        }
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
            if (current == null) {
                break;
            }
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
            if ((AND.equals(entry.getKey()) || OR.equals(entry.getKey())) && value instanceof List<?> list) {
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

    private @Nullable Object extractPropertyFromSingleArg(@Nullable Object[] methodParams, String property) {
        if (methodParams == null || methodParams.length != 1 || methodParams[0] == null) {
            return null;
        }
        Object arg = methodParams[0];
        if ("id".equals(property) || "_id".equals(property)) {
            return entityMapper.toNitriteFilterValue(arg);
        }
        String[] parts = property.split("\\.", -1);
        for (int start = 0; start < parts.length; start++) {
            Object current = arg;
            for (int i = start; i < parts.length; i++) {
                current = readSegmentValue(current, parts[i]);
                if (current == null) {
                    break;
                }
            }
            if (current != null) {
                return entityMapper.toNitriteFilterValue(current);
            }
        }
        return entityMapper.toNitriteFilterValue(arg);
    }

    @Nullable
    private Object readSegmentValue(@Nullable Object current, String segment) {
        if (current == null) {
            return null;
        }
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
            Document doc = entityMapper.convertValueToDocument(current);
            if (doc == null) {
                return null;
            }
            Object v = doc.get(segment);
            return v != null || segment.equals(alt) ? v : doc.get(alt);
        } catch (RuntimeException e) {
            // Expected when segment traversal reaches a scalar value (e.g. a String or
            // Number) that cannot be converted to a Document; treat as "no such segment".
            LOG.debug("Could not resolve segment '{}' on value of type {}: {}",
                segment, current.getClass(), e.getMessage());
            return null;
        }
    }

    private @Nullable Object toFilterValue(@Nullable Object value) {
        return entityMapper.toFilterValue(value);
    }
}
