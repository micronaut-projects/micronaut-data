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

import org.jspecify.annotations.Nullable;

/**
 * Runtime definition for one {@code @ChangeListener} method.
 *
 * <p>The table identity comes from the entity mapping. A schema is required to match only when
 * the mapping specifies one.</p>
 *
 * @param channel the notification channel
 * @param schema the optional mapped schema name, normalized for PostgreSQL identifier matching
 * @param table the mapped table name, normalized for PostgreSQL identifier matching
 * @param loader the loader for current entity state
 */
record PostgresChangeListenerDefinition(String channel, @Nullable String schema, String table,
                                        PostgresChangeListenerEntityLoader<?> loader) {

    /**
     * Matches the reported table and, when explicitly mapped, its schema.
     *
     * @param changedSchema the schema reported by the trigger
     * @param changedTable the table reported by the trigger
     * @return {@code true} if the notification matches this mapping
     */
    boolean matches(String changedSchema, String changedTable) {
        return table.equals(changedTable) && (schema == null || schema.equals(changedSchema));
    }
}
