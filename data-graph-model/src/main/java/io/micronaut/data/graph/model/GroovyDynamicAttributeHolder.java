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
package io.micronaut.data.graph.model;

import org.jspecify.annotations.Nullable;

/**
 * Groovy syntax for the schemaless properties of a {@link DynamicAttributeHolder}: subscripts and properties the class
 * does not declare. The graph processor adds this interface at build time to Groovy classes that implement
 * {@link DynamicAttributeHolder}, so Java, Kotlin and Python classes do not carry these methods.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public interface GroovyDynamicAttributeHolder extends DynamicAttributeHolder {

    /**
     * Groovy subscript read: {@code entity['name']}.
     *
     * @param name The property name
     * @return The value, or null if not set
     */
    default @Nullable Object getAt(String name) {
        return get(name);
    }

    /**
     * Groovy subscript write: {@code entity['name'] = value}.
     *
     * @param name  The property name
     * @param value The value
     */
    default void putAt(String name, @Nullable Object value) {
        set(name, value);
    }

    /**
     * Groovy property read for a property the class does not declare: {@code entity.name}.
     *
     * @param name The property name
     * @return The value, or null if not set
     */
    default @Nullable Object propertyMissing(String name) {
        return get(name);
    }

    /**
     * Groovy property write for a property the class does not declare: {@code entity.name = value}.
     *
     * @param name  The property name
     * @param value The value
     */
    default void propertyMissing(String name, @Nullable Object value) {
        set(name, value);
    }
}
