package io.micronaut.data.nitrite.runtime.query.ast;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

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
    @Nullable Object resolve(Object[] params, Map<String, Object> namedParameters);

    /**
     * A literal value that doesn't change.
     *
     * @param value the literal value
     */
    record Literal(@Nullable Object value) implements CompiledValue {
        @Override
        public @Nullable Object resolve(Object[] params, Map<String, Object> namedParameters) {
            return value;
        }
    }

    /**
     * A positional parameter value (e.g. $mn_qp:0).
     *
     * @param index the parameter index
     */
    record Parameter(int index) implements CompiledValue {
        @Override
        public @Nullable Object resolve(Object[] params, Map<String, Object> namedParameters) {
            return params != null && index >= 0 && index < params.length ? params[index] : null;
        }
    }

    /**
     * A named parameter value (e.g. :name).
     *
     * @param name the parameter name
     */
    record NamedParameter(String name) implements CompiledValue {
        @Override
        public @Nullable Object resolve(Object[] params, Map<String, Object> namedParameters) {
            return namedParameters != null ? namedParameters.get(name) : null;
        }
    }
}
