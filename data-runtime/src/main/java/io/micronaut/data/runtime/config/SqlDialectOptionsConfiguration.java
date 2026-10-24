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
package io.micronaut.data.runtime.config;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common SQL dialect option configuration shared by JDBC and R2DBC datasource configuration.
 *
 * @since 5.2
 */
public class SqlDialectOptionsConfiguration {

    private static final Pattern VERSION_LABEL = Pattern.compile("Version (\\d+)\\.(\\d+)");
    private static final Pattern MAJOR_MINOR = Pattern.compile("(\\d+)\\.(\\d+)");

    @Nullable
    private String version;

    private boolean detectVersion;

    /**
     * @return The target dialect version.
     * @since 5.2
     */
    @Nullable
    public String getVersion() {
        return version;
    }

    /**
     * @param version The target dialect version.
     * @since 5.2
     */
    public void setVersion(@Nullable String version) {
        this.version = version;
    }

    /**
     * @return Whether schema generation and validation target the version reported by the database when no
     * {@link #getVersion() version} is configured.
     * @since 5.3
     */
    public boolean isDetectVersion() {
        return detectVersion;
    }

    /**
     * Opts in to targeting the version reported by the database for schema generation and validation when no
     * {@link #getVersion() version} is configured. Repository SQL is generated at compilation time and is not
     * affected.
     *
     * @param detectVersion Whether to detect the database version
     * @since 5.3
     */
    public void setDetectVersion(boolean detectVersion) {
        this.detectVersion = detectVersion;
    }

    /**
     * Resolves the dialect version for runtime components such as schema generation: the configured version, or
     * the detected one when {@link #isDetectVersion() detection} is enabled.
     *
     * @param detector Detects the database version, returning {@code null} when it is unknown
     * @return The dialect version, or {@code null} for none
     * @since 5.3
     */
    public @Nullable String resolveVersion(Supplier<@Nullable String> detector) {
        if (version != null && !version.isBlank()) {
            return version;
        }
        return detectVersion ? detector.get() : null;
    }

    /**
     * Extracts the {@code major.minor} version from a database version text, such as
     * {@code Oracle AI Database 26ai Free Release 23.26.3.0.0 - ... Version 23.26.3.0.0} or {@code 23.26.3.0.0}.
     *
     * @param text The version text reported by the database or driver
     * @return The {@code major.minor} version, or {@code null} when the text has none
     * @since 5.3
     */
    public static @Nullable String parseMajorMinor(@Nullable String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = VERSION_LABEL.matcher(text);
        if (!matcher.find()) {
            matcher = MAJOR_MINOR.matcher(text);
            if (!matcher.find()) {
                return null;
            }
        }
        return matcher.group(1) + "." + matcher.group(2);
    }

}
