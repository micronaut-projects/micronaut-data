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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.version.SemanticVersion;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolved SQL dialect options used during SQL generation.
 *
 * @param dialect The dialect these options apply to
 * @param version The target dialect version
 */
@Internal
public record SqlDialectOptions(
    Dialect dialect,
    Optional<String> version
) {

    /**
     * Oracle version that enables lock-free reservation generation.
     */
    public static final String ORACLE_26_0_VERSION = "26.0.0";

    /**
     * Annotation/configuration member for target dialect version.
     */
    public static final String MEMBER_VERSION = "version";

    /**
     * Creates dialect options.
     *
     * @param dialect The dialect
     * @param version The target dialect version
     */
    public SqlDialectOptions {
        Objects.requireNonNull(dialect, "Dialect cannot be null");
        Objects.requireNonNull(version, "Version cannot be null");
        version = version.flatMap(SqlDialectOptions::normalizeVersionOptional);
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
     * @param version The target dialect version
     * @return resolved dialect options
     */
    public static SqlDialectOptions of(Dialect dialect, @Nullable String version) {
        Optional<String> versionValue = Optional.ofNullable(version)
            .filter(val -> !val.isBlank())
            .flatMap(SqlDialectOptions::normalizeVersionOptional);
        return new SqlDialectOptions(dialect, versionValue);
    }

    /**
     * @param requiredVersion The required target dialect version
     * @return true if the configured target dialect version is at least the required version
     */
    public boolean isVersionAtLeast(@Nullable String requiredVersion) {
        if (requiredVersion == null || requiredVersion.isBlank()) {
            return false;
        }
        try {
            String normalizedRequiredVersion = normalizeVersion(requiredVersion);
            return version
                .filter(configuredVersion -> SemanticVersion.isAtLeast(configuredVersion, normalizedRequiredVersion))
                .isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String normalizeVersion(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be blank");
        }
        String[] parts = normalized.split("\\.", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("Version must use at most major.minor.patch");
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Version parts cannot be blank");
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    throw new IllegalArgumentException("Version must use dot-separated numeric notation");
                }
            }
        }
        return switch (parts.length) {
            case 1 -> normalized + ".0.0";
            case 2 -> normalized + ".0";
            case 3 -> normalized;
            default -> throw new IllegalArgumentException("Version cannot be empty");
        };
    }

    private static Optional<String> normalizeVersionOptional(String value) {
        try {
            return Optional.of(normalizeVersion(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
