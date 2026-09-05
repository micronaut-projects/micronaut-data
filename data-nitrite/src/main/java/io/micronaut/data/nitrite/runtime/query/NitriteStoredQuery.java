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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledNitriteFilter;

import java.util.Map;

/**
 * A {@link StoredQuery} specialized for Nitrite.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @since 5.2.0
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
