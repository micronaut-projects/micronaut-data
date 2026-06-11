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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolved SQL dialect options used during SQL generation and runtime binding.
 *
 * @param dialect The dialect these options apply to.
 * @param compatibility A named compatibility level.
 */
@Internal
public record SqlDialectOptions(
    Dialect dialect,
    Optional<String> compatibility
) {

    /**
     * Compatibility level that enables Oracle 23 compatible SQL generation.
     */
    public static final String ORACLE_23_COMPATIBILITY = "ORACLE_23";

    /**
     * Annotation processor option prefix for SQL dialect compatibility.
     */
    public static final String DIALECT_OPTIONS_CONFIGURATION_PREFIX = "micronaut.data.sql.dialect-options";

    /**
     * Annotation/configuration member for compatibility.
     */
    public static final String MEMBER_COMPATIBILITY = "dialectOptionsCompatibility";

    /**
     * Creates dialect options.
     *
     * @param dialect The dialect
     * @param compatibility The compatibility value
     */
    public SqlDialectOptions {
        Objects.requireNonNull(dialect, "Dialect cannot be null");
        Objects.requireNonNull(compatibility, "Compatibility cannot be null");
        compatibility = compatibility.map(SqlDialectOptions::normalize);
    }

    /**
     * @param dialect The dialect
     * @return default options for the dialect
     */
    public static SqlDialectOptions defaults(Dialect dialect) {
        return new SqlDialectOptions(dialect, Optional.empty());
    }

    /**
     * Create options from explicit values.
     *
     * @param dialect The dialect
     * @param compatibility The compatibility
     * @return resolved dialect options
     */
    public static SqlDialectOptions of(Dialect dialect, @Nullable String compatibility) {
        Optional<String> compatibilityValue = Optional.ofNullable(compatibility)
            .filter(val -> !val.isBlank())
            .map(SqlDialectOptions::normalize);
        return new SqlDialectOptions(dialect, compatibilityValue);
    }

    /**
     * Resolve options from annotation metadata.
     *
     * @param annotationMetadata The annotation metadata
     * @param dialect The dialect
     * @return resolved dialect options
     */
    public static SqlDialectOptions of(AnnotationMetadata annotationMetadata, Dialect dialect) {
        String compatibility = annotationMetadata.stringValue(SqlQueryConfiguration.class, MEMBER_COMPATIBILITY).orElse(null);
        return of(dialect, compatibility);
    }

    /**
     * Resolve the annotation processor option key for a dialect compatibility value.
     *
     * @param dialect The dialect
     * @return The annotation processor option key
     */
    public static String compatibilityConfiguration(Dialect dialect) {
        Objects.requireNonNull(dialect, "Dialect cannot be null");
        return DIALECT_OPTIONS_CONFIGURATION_PREFIX + "." + normalizeDialectName(dialect) + ".compatibility";
    }

    /**
     * @param compatibility The compatibility level
     * @return true if the configured compatibility matches
     */
    public boolean hasCompatibility(@Nullable String compatibility) {
        if (compatibility == null || compatibility.isBlank()) {
            return false;
        }
        return this.compatibility.filter(normalize(compatibility)::equals).isPresent();
    }

    private static String normalize(String value) {
        return value.trim().replace('-', '_').toUpperCase(Locale.ENGLISH);
    }

    private static String normalizeDialectName(Dialect dialect) {
        return dialect.name().toLowerCase(Locale.ENGLISH).replace('_', '-');
    }
}
