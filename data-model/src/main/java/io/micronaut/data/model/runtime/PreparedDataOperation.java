/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.attr.AttributeHolder;

import java.util.Optional;

/**
 * An operation that has been prepared for execution with the current context.
 *
 * @author graemerocher
 * @since 2.2.0
 * @param <R> The result type
 */
public interface PreparedDataOperation<R> extends StoredDataOperation<R>, AttributeHolder {

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
