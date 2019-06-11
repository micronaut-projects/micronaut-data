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
package io.micronaut.data.mapper;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.reflect.exception.InstantiationException;

/**
 * A context object to facilitate and ease mapping objects programmatically.
 * @param <D> The result set type.
 * @author graemerocher
 * @since 1.0.0
 */
public interface DataMapper<D> {

    /**
     * Map the given result set to the given object.
     * @param resultSet The result set
     * @param type The type
     * @param <R> The result generic type
     * @return The mapped object
     * @throws InstantiationException If the object cannot be mapped.
     */
    <R> R map(D resultSet, Class<R> type) throws InstantiationException;

    /**
     * Read and convert a value for the given name from the given result type.
     * @param resultSet The result type
     * @param name The name
     * @return The mapped object
     * @throws ConversionErrorException If the value could not be converted
     */
    Object read(D resultSet, String name) throws ConversionErrorException;

    /**
     * @return The conversion service to use.
     */
    default ConversionService<?> getConversionService() {
        return ConversionService.SHARED;
    }
}
