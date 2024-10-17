/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * The client information that can be used to set to {@link java.sql.Connection#setClientInfo(String, String)}.
 * Currently used only for Oracle database connections.
 *
 * @param appName  The app name corresponding to the micronaut.application.name config value and can be null
 * @param module   The module
 * @param action   The action
 */
public record ConnectionClientTracingInfo(@Nullable String appName, @NonNull String module, @NonNull String action) {
}
