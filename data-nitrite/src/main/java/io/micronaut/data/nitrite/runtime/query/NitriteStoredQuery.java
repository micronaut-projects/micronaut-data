package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledNitriteFilter;

import java.util.Map;

/**
 * A {@link StoredQuery} specialized for Nitrite.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @since 1.0.0
 */
@Internal
public interface NitriteStoredQuery<E, R> extends StoredQuery<E, R> {

  /**
   * Returns the filter map.
   * @return The pre-parsed filter structure if available (for JSON queries)
   */
  @Nullable
  Map<String, Object> getFilterMap();

  /**
   * Returns the compiled filter.
   * @return The pre-compiled filter structure if available
   */
  @Nullable
  default CompiledNitriteFilter getCompiledFilter() {
    return null;
  }

  /**
   * Returns the update map.
   * @return The pre-parsed update structure ($set) if available
   */
  @Nullable
  Map<String, Object> getUpdateMap();
}
