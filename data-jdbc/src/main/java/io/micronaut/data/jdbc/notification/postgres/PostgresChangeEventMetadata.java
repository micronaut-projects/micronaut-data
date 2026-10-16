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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.jdbc.notification.ChangeEventMetadata;

import java.util.Map;
import java.util.Objects;

/**
 * PostgreSQL notification metadata identifying the affected table and row keys.
 *
 * <p>Keys use exact database column names, including case, rather than Java property names.
 * Values are the textual representation of PostgreSQL JSON scalar values, without surrounding
 * JSON quotes. In particular, numeric identifiers retain their precision. Both maps are immutable
 * and support composite keys. Inserts have no old keys, deletes have no new keys, and updates
 * retain both, including when a primary key changes. Table invalidations have neither.</p>
 *
 * @param channel The notification channel.
 * @param schema  The exact database schema name.
 * @param table   The exact database table name.
 * @param oldKeys The old primary-key values, or an empty map when unavailable.
 * @param newKeys The new primary-key values, or an empty map when unavailable.
 * @since 5.3.0
 */
@Experimental
public record PostgresChangeEventMetadata(String channel,
                                          String schema,
                                          String table,
                                          Map<String, String> oldKeys,
                                          Map<String, String> newKeys) implements ChangeEventMetadata {

    /**
     * Creates metadata with defensive copies of the primary-key maps.
     *
     * @param channel The notification channel.
     * @param schema  The database schema name.
     * @param table   The database table name.
     * @param oldKeys The old primary-key values.
     * @param newKeys The new primary-key values.
     */
    public PostgresChangeEventMetadata {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(table, "table");
        oldKeys = Map.copyOf(oldKeys);
        newKeys = Map.copyOf(newKeys);
    }
}
