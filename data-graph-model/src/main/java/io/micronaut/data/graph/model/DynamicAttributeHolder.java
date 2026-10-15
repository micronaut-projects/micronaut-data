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

import java.util.Map;

/**
 * Gives an entity map-like access to its schemaless properties, held by its
 * {@link io.micronaut.data.graph.annotation.DynamicAttributes} property.
 *
 * <ul>
 *     <li>Java: {@code person.set("nickname", "Neo")} and {@code person.get("nickname")}.</li>
 *     <li>Kotlin: {@code person["nickname"] = "Neo"} and {@code person["nickname"]}, through the {@code get} and
 *     {@code set} methods.</li>
 *     <li>Groovy: {@code person['nickname'] = 'Neo'} and {@code person.nickname}, through {@code getAt},
 *     {@code putAt} and {@code propertyMissing}.</li>
 * </ul>
 *
 * @author graemerocher
 * @since 5.3.0
 */
public interface DynamicAttributeHolder {

    /**
     * @return The mutable map of schemaless properties
     */
    Map<String, Object> dynamicAttributes();

    /**
     * @param name The property name
     * @return The value, or null if not set
     */
    default @Nullable Object get(String name) {
        return dynamicAttributes().get(name);
    }

    /**
     * @param name The property name
     * @param type The expected type
     * @param <T>  The expected type
     * @return The value, or null if not set
     */
    default <T> @Nullable T get(String name, Class<T> type) {
        return type.cast(dynamicAttributes().get(name));
    }

    /**
     * Sets a schemaless property. A null value removes the property when the entity is saved.
     *
     * @param name  The property name
     * @param value The value
     */
    default void set(String name, @Nullable Object value) {
        dynamicAttributes().put(name, value);
    }

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
