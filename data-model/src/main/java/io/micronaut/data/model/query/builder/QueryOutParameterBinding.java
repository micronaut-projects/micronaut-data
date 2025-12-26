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
package io.micronaut.data.model.query.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.DataType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Describes an OUT parameter binding for a SQL query (for example Oracle RETURNING ... INTO ...).
 *
 * This metadata is attached to {@link QueryResult} and can be propagated into the runtime
 * to register CallableStatement OUT parameters with correct ordering and types.
 *
 * @since 5.0
 */
@Experimental
public interface QueryOutParameterBinding {

    /**
     * @return The name of the column/parameter (when available).
     */
    @Nullable
    default String getName() {
        return null;
    }

    /**
     * @return The required name of the parameter or throws exception.
     */
    @NonNull
    default String getRequiredName() {
        String name = getName();
        if (name == null) {
            throw new IllegalStateException("Parameter name cannot be null for a query out parameter: " + this);
        }
        return name;
    }

    /**
     * @return The data type, when known.
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
     * @return The parameter binding property path (for method argument binding), if any.
     */
    @Nullable
    default String[] getParameterBindingPath() {
        return null;
    }

    /**
     * @return The entity property path to map this OUT parameter back into, if applicable.
     */
    @Nullable
    default String[] getPropertyPath() {
        return null;
    }

    /**
     * @return The required property path or throws an exception.
     */
    @NonNull
    default String[] getRequiredPropertyPath() {
        String[] propertyPath = getPropertyPath();
        if (propertyPath == null) {
            throw new IllegalStateException("Property path cannot be null for a query out parameter: " + this);
        }
        return propertyPath;
    }
}
