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
package io.micronaut.data.jdbc.notification.postgres;

import io.micronaut.core.annotation.Internal;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable metadata captured from a PostgreSQL trigger notification.
 *
 * @param channel The notification channel
 * @param schema The reported database schema
 * @param table The reported unqualified table name
 * @param oldKeys The old primary-key values
 * @param newKeys The new primary-key values
 */
@Internal
record DefaultPostgresChangeEventMetadata(String channel,
                                          String schema,
                                          String table,
                                          Map<String, String> oldKeys,
                                          Map<String, String> newKeys) implements PostgresChangeEventMetadata {

    DefaultPostgresChangeEventMetadata {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(table, "table");
        oldKeys = Map.copyOf(oldKeys);
        newKeys = Map.copyOf(newKeys);
    }
}
