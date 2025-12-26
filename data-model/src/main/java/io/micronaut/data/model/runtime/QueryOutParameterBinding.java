/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.DataType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Runtime OUT parameter binding metadata for a stored query (e.g. Oracle RETURNING ... INTO ...).
 * Mirrors the builder-time metadata and is used by repository operations to register
 * CallableStatement OUT parameters in correct order and JDBC type.
 *
 * @since 5.0
 */
@Experimental
public interface QueryOutParameterBinding {

    /**
     * @return The name of the OUT column/parameter if available.
     */
    @Nullable
    default String getName() {
        return null;
    }

    /**
     * @return The required name or throws.
     */
    @NonNull
    default String getRequiredName() {
        String name = getName();
        if (name == null) {
            throw new IllegalStateException("OUT parameter name cannot be null: " + this);
        }
        return name;
    }

    /**
     * @return The data type for the OUT parameter when known.
     */
    @Nullable
    default DataType getDataType() {
        return null;
    }

    /**
     * @return The parameter converter class, when used.
     */
    @Nullable
    default Class<?> getParameterConverterClass() {
        return null;
    }

    /**
     * @return The parameter binding property path (when sourced from a method argument), if any.
     */
    @Nullable
    default String[] getParameterBindingPath() {
        return null;
    }

    /**
     * @return The entity property path this OUT parameter should map back into, if applicable.
     */
    @Nullable
    default String[] getPropertyPath() {
        return null;
    }
}
