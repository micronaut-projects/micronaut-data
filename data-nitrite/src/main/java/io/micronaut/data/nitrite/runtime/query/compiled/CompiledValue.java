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

import java.util.Map;

/**
 * A pre-compiled value that can be resolved with parameters.
 *
 * @since 1.0.0
 */
@Internal
@FunctionalInterface
public interface CompiledValue {

    /**
     * Resolve the value.
     *
     * @param params          positional parameters
     * @param namedParameters named parameters
     * @return the resolved value
     */
    Object resolve(Object[] params, Map<String, Object> namedParameters);

    /**
     * A literal value that doesn't change.
     */
    record Literal(Object value) implements CompiledValue {
        @Override
        public Object resolve(Object[] params, Map<String, Object> namedParameters) {
            return value;
        }
    }

    /**
     * A positional parameter value (e.g. $mn_qp:0).
     */
    record Parameter(int index) implements CompiledValue {
        @Override
        public Object resolve(Object[] params, Map<String, Object> namedParameters) {
            return params != null && index >= 0 && index < params.length ? params[index] : null;
        }
    }

    /**
     * A named parameter value (e.g. :name).
     */
    record NamedParameter(String name) implements CompiledValue {
        @Override
        public Object resolve(Object[] params, Map<String, Object> namedParameters) {
            return namedParameters != null ? namedParameters.get(name) : null;
        }
    }
}
