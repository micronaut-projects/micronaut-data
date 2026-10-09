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
package io.micronaut.data.model;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Shared Oracle change-notification option restrictions for compile-time and runtime validation.
 *
 * <p>Option names and values are plain strings so annotation processing does not require the
 * Oracle JDBC driver. Callers add diagnostics for the listener or datasource being validated.
 * Unknown options are left to the driver, and source-specific checks remain with the caller.</p>
 */
@Internal
public final class OracleChangeNotificationOptions {

    private OracleChangeNotificationOptions() {
    }

    /**
     * Returns the shared error for an unsupported option, without caller-specific context.
     *
     * @param name  the option name
     * @param value the configured option value
     * @return the validation error, or {@code null} when the option is allowed
     */
    public static @Nullable String invalidOption(String name, String value) {
        if (name.isBlank()) {
            return "has an Oracle property with a blank name";
        }
        if ("DCN_CLIENT_INIT_REGID".equals(name)) {
            return name + ": reusing an existing reliable DCN registration is not supported";
        }
        if ("DCN_NOTIFY_ROWIDS".equals(name) && !"true".equalsIgnoreCase(value)) {
            return "requires " + name + " to be true so row-level operation and ROWID details are available";
        }
        if ("DCN_NOTIFY_CHANGELAG".equals(name) && !"0".equals(value.trim())) {
            return "requires " + name + " to be 0 so row-level operation and ROWID details are available";
        }
        if ("NTF_GROUPING_CLASS".equals(name)) {
            if (!"NTF_GROUPING_CLASS_NONE".equals(value)) {
                return name + ": notification grouping is not supported";
            }
        } else if ("NTF_GROUPING_VALUE".equals(name)
            || "NTF_GROUPING_TYPE".equals(name)
            || "NTF_GROUPING_REPEAT_TIME".equals(name)
            || "NTF_GROUPING_START_TIME".equals(name)) {
            return name + ": notification grouping is not supported";
        }
        if ("DCN_PULL_NOTIFICATIONS".equals(name) && !"false".equalsIgnoreCase(value)) {
            return name + " [" + value + "] is not supported because AQ pull delivery does not invoke the listener callback";
        }
        if ("DCN_PULL_QUEUE_NAME".equals(name)) {
            return name + " is not supported because AQ pull delivery does not invoke the listener callback";
        }
        if ("NTF_TIMEOUT".equals(name)) {
            try {
                if (Integer.parseInt(value) < 0) {
                    return name + " must be a non-negative integer number of seconds";
                }
            } catch (NumberFormatException e) {
                return name + " must be a non-negative integer number of seconds";
            }
        }
        return null;
    }

}
