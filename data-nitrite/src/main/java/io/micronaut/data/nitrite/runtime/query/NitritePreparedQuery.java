package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.runtime.PreparedQuery;
import org.dizitart.no2.filters.Filter;

/**
 * A {@link PreparedQuery} specialized for Nitrite.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @since 1.0.0
 */
@Internal
public interface NitritePreparedQuery<E, R> extends PreparedQuery<E, R>, NitriteStoredQuery<E, R> {

  /**
   * Returns the nitrite filter.
   * @return The pre-calculated Nitrite filter
   */
  @NonNull
  Filter getNitriteFilter();
}
