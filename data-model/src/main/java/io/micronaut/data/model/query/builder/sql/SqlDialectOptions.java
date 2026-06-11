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

import java.util.ArrayList;
import java.util.List;
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
     * Compatibility baseline that enables Oracle 23.1 compatible SQL generation.
     */
    public static final String ORACLE_23_1_COMPATIBILITY = "ORACLE_23_1";

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

    /**
     * @param requiredCompatibility The required compatibility baseline
     * @return true if the configured compatibility is at least the required compatibility baseline
     */
    public boolean isAtLeast(@Nullable String requiredCompatibility) {
        if (requiredCompatibility == null || requiredCompatibility.isBlank()) {
            return false;
        }
        Optional<CompatibilityBaseline> requiredBaseline = parseCompatibility(requiredCompatibility);
        if (requiredBaseline.isEmpty() || requiredBaseline.get().dialect() != dialect) {
            return false;
        }
        return compatibility
            .flatMap(SqlDialectOptions::parseCompatibility)
            .filter(configuredBaseline -> configuredBaseline.isAtLeast(requiredBaseline.get()))
            .isPresent();
    }

    private static String normalize(String value) {
        return value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ENGLISH);
    }

    private static String normalizeDialectName(Dialect dialect) {
        return dialect.name().toLowerCase(Locale.ENGLISH).replace('_', '-');
    }

    private static Optional<CompatibilityBaseline> parseCompatibility(String value) {
        String normalized = normalize(value);
        for (Dialect dialect : Dialect.values()) {
            String prefix = normalize(dialect.name());
            if (normalized.startsWith(prefix + "_")) {
                return parseVersion(normalized.substring(prefix.length() + 1))
                    .map(version -> new CompatibilityBaseline(dialect, version));
            }
        }
        return Optional.empty();
    }

    private static Optional<List<Integer>> parseVersion(String version) {
        List<Integer> parsed = new ArrayList<>();
        int currentPart = 0;
        boolean hasDigit = false;
        for (int i = 0; i < version.length(); i++) {
            char ch = version.charAt(i);
            if (ch == '_') {
                if (!hasDigit) {
                    return Optional.empty();
                }
                parsed.add(currentPart);
                currentPart = 0;
                hasDigit = false;
                continue;
            }
            if (!Character.isDigit(ch)) {
                return Optional.empty();
            }
            int digit = ch - '0';
            if (currentPart > (Integer.MAX_VALUE - digit) / 10) {
                return Optional.empty();
            }
            currentPart = currentPart * 10 + digit;
            hasDigit = true;
        }
        if (!hasDigit) {
            return Optional.empty();
        }
        parsed.add(currentPart);
        return Optional.of(parsed);
    }

    private record CompatibilityBaseline(Dialect dialect, List<Integer> version) {

        boolean isAtLeast(CompatibilityBaseline required) {
            if (dialect != required.dialect) {
                return false;
            }
            int length = Math.max(version.size(), required.version.size());
            for (int i = 0; i < length; i++) {
                int configuredPart = i < version.size() ? version.get(i) : 0;
                int requiredPart = i < required.version.size() ? required.version.get(i) : 0;
                if (configuredPart != requiredPart) {
                    return configuredPart > requiredPart;
                }
            }
            return true;
        }
    }
}
