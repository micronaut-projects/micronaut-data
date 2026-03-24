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
package io.micronaut.data.nitrite.runtime.query.compiled;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A structured AST for Nitrite filters.
 *
 * @since 1.0.0
 */
@Internal
public sealed interface NitriteFilterAST extends CompiledNitriteFilter {

    @Override
    default Filter bind(Object[] params, Map<String, Object> namedParameters) {
        return toFilter(params, namedParameters);
    }

    /**
     * Convert this AST node to a Nitrite Filter using the provided parameters.
     *
     * @param params positional parameters
     * @param namedParameters named parameters
     * @return the Nitrite Filter
     */
    Filter toFilter(Object[] params, Map<String, Object> namedParameters);

    /**
     * Logical AND of multiple filters.
     */
    record AndNode(List<NitriteFilterAST> children) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            List<Filter> results = new ArrayList<>(children.size());
            for (NitriteFilterAST child : children) {
                Filter f = child.toFilter(params, namedParameters);
                if (f != null && f != Filter.ALL) {
                    results.add(f);
                }
            }
            return results.isEmpty() ? Filter.ALL : results.size() == 1 ? results.get(0) : Filter.and(results.toArray(new Filter[0]));
        }
    }

    /**
     * Logical OR of multiple filters.
     */
    record OrNode(List<NitriteFilterAST> children) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            List<Filter> results = new ArrayList<>(children.size());
            for (NitriteFilterAST child : children) {
                Filter f = child.toFilter(params, namedParameters);
                if (f != null && f != Filter.ALL) {
                    results.add(f);
                }
            }
            return results.isEmpty() ? Filter.ALL : results.size() == 1 ? results.get(0) : Filter.or(results.toArray(new Filter[0]));
        }
    }

    /**
     * A filter for a specific field and set of operators.
     */
    record FieldNode(
        NitriteFilterBuilder builder,
        RuntimePersistentEntity<?> entity,
        String rawField,
        Map<String, CompiledValue> operators
    ) implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            if (operators.size() == 1 && operators.containsKey("$eq")) {
                Object resolvedValue = operators.get("$eq").resolve(params, namedParameters);
                return builder.buildFieldFilter(entity, rawField, Collections.singletonMap("$eq", resolvedValue), params, namedParameters);
            }
            Map<String, Object> resolvedOperators = new LinkedHashMap<>(operators.size());
            for (Map.Entry<String, CompiledValue> entry : operators.entrySet()) {
                resolvedOperators.put(entry.getKey(), entry.getValue().resolve(params, namedParameters));
            }
            return builder.buildFieldFilter(entity, rawField, resolvedOperators, params, namedParameters);
        }
    }

    /**
     * A filter that matches everything.
     */
    record AllNode() implements NitriteFilterAST {
        @Override
        public Filter toFilter(Object[] params, Map<String, Object> namedParameters) {
            return Filter.ALL;
        }
    }
}
