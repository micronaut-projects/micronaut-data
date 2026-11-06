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
package io.micronaut.data.graph.runtime;

/**
 * Encodes values of a type into values a graph database accepts as parameters or properties, as a
 * {@link io.micronaut.core.convert.TypeConverter} converts between types. Encoders are registered with
 * {@link GraphValueEncoders} for the type they encode and apply to its subtypes.
 *
 * @param <T> The type of value encoded
 * @author graemerocher
 * @since 5.3.0
 */
@FunctionalInterface
public interface GraphValueEncoder<T> {

    /**
     * Encodes a value.
     *
     * @param value    The value, never null
     * @param encoders The encoders, to encode the elements of containers
     * @return The encoded value
     */
    Object encode(T value, GraphValueEncoders encoders);
}
