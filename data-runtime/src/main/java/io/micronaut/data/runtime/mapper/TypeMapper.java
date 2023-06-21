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
package io.micronaut.data.runtime.mapper;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;

/**
 * A context object to facilitate and ease mapping objects programmatically.
 *
 * @param <D> The source type.
 * @param <R> The result type
 * @author graemerocher
 * @since 1.0.0
 */
public interface TypeMapper<D, R> {

    /**
     * Map the given result set to the given object.
     * @param object The object to map
     * @param type The type
     * @return The mapped object
     * @throws DataAccessException If the object cannot be mapped.
     */
    @NonNull R map(@NonNull D object, @NonNull Class<R> type) throws DataAccessException;

    /**
     * Read a value for the given name from the given object.
     * @param object The object to read from
     * @param name The name
     * @return The value
     */
    @Nullable Object read(@NonNull D object, @NonNull String name);

    /**
     * Read a value for the given name from the given object.
     * @param object The object to read from
     * @param argument The argument
     * @return The value
     */
    default @Nullable Object read(@NonNull D object, @NonNull Argument<?> argument) {
        return read(object, argument.getName());
    }

    /**
     * @return The conversion service to use.
     */
    default @NonNull ConversionService getConversionService() {
        return ConversionService.SHARED;
    }
}
