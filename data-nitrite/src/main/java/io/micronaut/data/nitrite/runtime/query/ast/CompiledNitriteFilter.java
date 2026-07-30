package io.micronaut.data.nitrite.runtime.query.ast;

import io.micronaut.core.annotation.Internal;
import org.dizitart.no2.filters.Filter;

import java.util.Map;

/**
 * A pre-compiled Nitrite filter that can be bound to actual parameter values.
 *
 * @since 1.0.0
 */
@Internal
@FunctionalInterface
public interface CompiledNitriteFilter {

    /**
     * Bind the compiled filter structure to actual parameter values.
     *
     * @param params          positional parameters
     * @param namedParameters named parameters
     * @return the ready-to-use Nitrite Filter
     */
    Filter bind(Object[] params, Map<String, Object> namedParameters);
}
