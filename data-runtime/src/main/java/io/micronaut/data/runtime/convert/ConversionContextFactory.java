package io.micronaut.data.runtime.convert;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.convert.DialectConversionContext;

/**
 * Factory to create ConversionContext instances used during SQL mapping.
 * that include datastore-specific details (e.g. dialect, connection).
 *
 * Default implementation in data-runtime provides lightweight contexts
 * delegating to {@code ConversionContext.of(...)} without datastore state.
 *
 * Datastore modules (e.g. JDBC) may provide richer contexts and still satisfy
 *
 * @since 4.x
 */
public interface ConversionContextFactory {

    /**
     * Create a conversion context for a Micronaut {@link Argument}.
     *
     * @param argument the argument
     * @return SQL conversion context
     */
    @NonNull
    DialectConversionContext forArgument(@NonNull Argument<?> argument);
}
