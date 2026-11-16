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
package io.micronaut.data.jdbc.notification.oracle;

import org.jspecify.annotations.Nullable;

/**
 * Oracle change-notification option restrictions for validation.
 *
 * <p>Callers add diagnostics for the listener or datasource being validated.
 * Unknown options are left to the driver. Keep these rules aligned with the corresponding
 * validator in the other module.</p>
 */
final class OracleChangeNotificationOptions {

    private OracleChangeNotificationOptions() {
    }

    /**
     * Returns the shared error for an unsupported option, without caller-specific context.
     *
     * @param name  the option name
     * @param value the configured option value
     * @return the validation error, or {@code null} when the option is allowed
     */
    static @Nullable String invalidOption(String name, String value) {
        if (name.isBlank()) {
            return "has an Oracle property with a blank name";
        }
        return switch (name) {
            case "DCN_CLIENT_INIT_REGID" -> name + ": reusing an existing reliable DCN registration is not supported";
            case "DCN_NOTIFY_ROWIDS" -> "true".equalsIgnoreCase(value) ? null
                : "requires " + name + " to be true so row-level operation and ROWID details are available";
            case "DCN_NOTIFY_CHANGELAG" -> "0".equals(value.trim()) ? null
                : "requires " + name + " to be 0 so row-level operation and ROWID details are available";
            case "NTF_GROUPING_CLASS" -> "NTF_GROUPING_CLASS_NONE".equals(value) ? null
                : name + ": notification grouping is not supported";
            case "NTF_GROUPING_VALUE", "NTF_GROUPING_TYPE", "NTF_GROUPING_REPEAT_TIME", "NTF_GROUPING_START_TIME" ->
                name + ": notification grouping is not supported";
            case "DCN_PULL_NOTIFICATIONS" -> "false".equalsIgnoreCase(value) ? null
                : name + " [" + value + "] is not supported because AQ pull delivery does not invoke the listener callback";
            case "DCN_PULL_QUEUE_NAME" ->
                name + " is not supported because AQ pull delivery does not invoke the listener callback";
            case "NTF_TIMEOUT" -> {
                try {
                    yield Integer.parseInt(value) < 0 ? name + " must be a non-negative integer number of seconds" : null;
                } catch (NumberFormatException _) {
                    yield name + " must be a non-negative integer number of seconds";
                }
            }
            default -> null;
        };
    }

}
