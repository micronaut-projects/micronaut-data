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
import io.micronaut.data.nitrite.runtime.mapping.NitriteTypeRegistry;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Internal
final class ValueResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ValueResolver.class);

    private final NitriteEntityMapper entityMapper;

    ValueResolver(NitriteEntityMapper entityMapper) {
        this.entityMapper = entityMapper;
    }

    Object resolveValue(Object value, Object[] params, Map<String, Object> namedParameters) {
        Object resolved = resolveValueInternal(value, params, namedParameters);
        LOG.debug("resolveValue: value={}, resolved={}", value, resolved);
        return resolved;
    }

    Object resolveValueInternal(Object value, Object[] params, Map<String, Object> namedParameters) {
        if (value instanceof String s) {
            if (s.startsWith("$mn_qp:") && s.indexOf("$mn_qp:", 7) < 0) {
                try {
                    int idx = Integer.parseInt(s.substring(7));
                    if (params != null && idx >= 0 && idx < params.length) return params[idx];
                } catch (Exception ignored) {
                    // Fall through if placeholder is not a valid integer
                }
            }
            if (s.contains("$mn_qp:")) {
                StringBuilder result = new StringBuilder();
                int pos = 0;
                while (pos < s.length()) {
                    int idx = s.indexOf("$mn_qp:", pos);
                    if (idx < 0) { result.append(s.substring(pos)); break; }
                    result.append(s, pos, idx);
                    int paramEnd = idx + 7;
                    while (paramEnd < s.length() && Character.isDigit(s.charAt(paramEnd))) paramEnd++;
                    try {
                        int paramIdx = Integer.parseInt(s.substring(idx + 7, paramEnd));
                        if (params != null && paramIdx >= 0 && paramIdx < params.length) {
                            Object paramValue = params[paramIdx];
                            result.append(paramValue != null ? paramValue.toString() : "");
                        }
                    } catch (Exception ignored) {
                        // If resolution fails for a specific placeholder, keep it as-is in the string
                        result.append(s, idx, paramEnd);
                    }
                    pos = paramEnd;
                }
                return result.toString();
            }
            if (s.startsWith(":")) {
                String name = s.substring(1);
                if (namedParameters.containsKey(name)) return namedParameters.get(name);
            }
        }
        if (value instanceof Map<?, ?> vm && vm.size() == 1 && vm.get("$mn_qp") instanceof Integer idx) {
            if (params != null && idx >= 0 && idx < params.length) return params[idx];
        }
        return value;
    }

    Object preConvertForFilter(Object value) {
        return NitriteTypeRegistry.write(value);
    }

    Object maybeCoerceUuid(String field, Object value) {
        if (value instanceof String s && ("id".equals(field) || "_id".equals(field))) {
            try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        }
        return value;
    }

    CompiledValue compileValue(Object value) {
        if (value instanceof String s) {
            if (s.startsWith("$mn_qp:") && s.indexOf("$mn_qp:", 7) < 0) {
                try {
                    return new CompiledValue.Parameter(Integer.parseInt(s.substring(7)));
                } catch (Exception ignored) {
                    // Fall through if placeholder is not a valid integer
                }
            }
            if (s.startsWith(":")) return new CompiledValue.NamedParameter(s.substring(1));
            if (s.contains("$mn_qp:")) return (params, named) -> resolveValueInternal(s, params, named);
            return new CompiledValue.Literal(s);
        }
        if (value instanceof Map<?, ?> vm && vm.size() == 1 && vm.get("$mn_qp") instanceof Integer idx) {
            return new CompiledValue.Parameter(idx);
        }
        return new CompiledValue.Literal(value);
    }

    List<Comparable<?>> resolveCollection(Object finalValue, Object[] params, Map<String, Object> namedParameters) {
        List<Comparable<?>> resolvedValues = new ArrayList<>();
        if (finalValue instanceof List<?> list && list.size() == 1 && isPlaceholder(list.getFirst())) {
            Object resolved = resolveValue(list.getFirst(), params, namedParameters);
            if (resolved instanceof Collection<?> coll) {
                for (Object item : coll) {
                    Object r = entityMapper.toNitriteFilterValue(preConvertForFilter(item));
                    if (r instanceof Comparable<?> c) resolvedValues.add(c);
                }
            } else if (resolved != null && resolved.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(resolved);
                for (int i = 0; i < len; i++) {
                    Object r = entityMapper.toNitriteFilterValue(preConvertForFilter(java.lang.reflect.Array.get(resolved, i)));
                    if (r instanceof Comparable<?> c) resolvedValues.add(c);
                }
            } else if (resolved instanceof Comparable<?> c) {
                resolvedValues.add(c);
            }
        } else if (finalValue instanceof Collection<?> coll) {
            for (Object item : coll) {
                Object r = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(item, params, namedParameters)));
                if (r instanceof Comparable<?> c) resolvedValues.add(c);
            }
        } else if (finalValue instanceof Object[] array) {
            for (Object item : array) {
                Object r = entityMapper.toNitriteFilterValue(preConvertForFilter(resolveValue(item, params, namedParameters)));
                if (r instanceof Comparable<?> c) resolvedValues.add(c);
            }
        }
        return resolvedValues;
    }

    private boolean isPlaceholder(Object value) {
        if (value instanceof String s && (s.startsWith("$mn_qp:") || s.startsWith(":"))) return true;
        return value instanceof Map<?, ?> vm && vm.size() == 1 && vm.containsKey("$mn_qp");
    }
}
