/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Interface that models a prepared query. A prepared query extends from {@link StoredQuery} and includes the bound parameter values.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <E> The entity type
 * @param <R> The result type
 */
public interface PreparedQuery<E, R> extends PagedQuery<E>, StoredQuery<E, R> {

    /**
     * @return The repository type.
     */
    Class<?> getRepositoryType();

    /**
     * @return The named parameter values
     */
    @NonNull
    Map<String, Object> getParameterValues();

    /**
     * The indexed parameter values if this is native query SQL query. Returns empty if {@link #isNative()} is false.
     * @return The indexed values
     */
    default @NonNull Map<Integer, Object> getIndexedParameterValues() {
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    default Map<String, Object> getQueryHints() {
        return Collections.emptyMap();
    }

    /**
     * Return the value of the given parameter if the given role.
     * @param role The role
     * @param type The type
     * @param <RT> The generic type
     * @return An optional value.
     */
    default <RT> Optional<RT> getParameterInRole(@NonNull String role, @NonNull Class<RT> type) {
        return Optional.empty();
    }
}
