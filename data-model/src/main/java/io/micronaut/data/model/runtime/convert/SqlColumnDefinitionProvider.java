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
import io.micronaut.data.model.query.builder.sql.Dialect;
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
     * Canonical database types supported across SQL and NoSQL stores.
     * Converters can prefer switching on this enum instead of vendor-specific enums.
     *
     * For SQL backends, use {@link #from(Dialect)} to translate from the SQL dialect.
     */
    enum DatabaseType {
        POSTGRES,
        MYSQL,
        ORACLE,
        SQL_SERVER,
        H2,
        // NoSQL/document stores supported by Micronaut Data:
        MONGODB,
        AZURE_COSMOS,
        // Fallback/unknown or unsupported mapping
        OTHER;

        /**
         * Map a SQL {@link Dialect} to a generic {@link DatabaseType}.
         * If the dialect is null or unknown, returns {@link #OTHER}.
         *
         * @param dialect SQL dialect (may be null)
         * @return The corresponding database type
         */
        public static DatabaseType from(@Nullable Dialect dialect) {
            if (dialect == null) {
                return OTHER;
            }
            return switch (dialect) {
                case POSTGRES -> POSTGRES;
                case MYSQL -> MYSQL;
                case ORACLE -> ORACLE;
                case SQL_SERVER -> SQL_SERVER;
                case H2 -> H2;
                // ANSI, SQLITE and any other future values map to OTHER by default
                default -> OTHER;
            };
        }
    }

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
