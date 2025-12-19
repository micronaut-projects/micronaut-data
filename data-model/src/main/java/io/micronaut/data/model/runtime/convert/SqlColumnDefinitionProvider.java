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
package io.micronaut.data.model.runtime.convert;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

/**
 * SPI that allows a converter to provide a vendor-specific SQL column definition during schema generation.
 *
 * Implementations can be discovered by SqlSchemaUtils without introducing cross-module dependencies.
 *
 * @since 5.0.0
 */
public interface SqlColumnDefinitionProvider {

    /**
      * Return a vendor-specific SQL column definition for this attribute, or {@code null} to delegate to default mapping.
      *
      * Implementations should inspect the provided {@link Argument} to extract length/precision/scale and relevant
      * annotations (e.g. {@code @jakarta.persistence.Column}, {@code @jakarta.validation.constraints.Size}) and
      * produce a dialect-specific column type.
      *
      * @param argument the Micronaut {@link Argument} describing the attribute (type + annotations)
      * @param databaseType The canonical database type for which a definition should be produced
      * @return the SQL column definition string, or {@code null} to allow default resolution
      */
     @Nullable
     String getColumnDefinition(Argument<?> argument, DatabaseType databaseType);

    /**
     * Whether this provider can handle the given attribute.
     *
     * @param argument the attribute argument
     * @return true if this provider can generate a column definition for the given argument
     */
    boolean supports(Argument<?> argument);
}
