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
import io.micronaut.data.notification.ChangeEventMetadata;

import java.util.Map;

/**
 * PostgreSQL notification metadata identifying the affected table and row keys.
 *
 * <p>Instances are supplied by the PostgreSQL notification provider.</p>
 *
 * <p>Keys use exact database column names, including case, rather than Java property names.
 * Values are the textual representation of PostgreSQL JSON scalar values, without surrounding
 * JSON quotes. In particular, numeric identifiers retain their precision. Both maps are immutable
 * and support composite keys. Inserts have no old keys, deletes have no new keys, and updates
 * retain both, including when a primary key changes. Table invalidations have neither.</p>
 *
 * @since 5.3.0
 */
@Experimental
public sealed interface PostgresChangeEventMetadata extends ChangeEventMetadata
    permits DefaultPostgresChangeEventMetadata {

    /**
     * Returns the channel on which the notification was published.
     *
     * @return The exact notification channel name.
     */
    String channel();

    /**
     * Returns the schema reported by PostgreSQL.
     *
     * @return The database schema name, preserving its spelling and case.
     */
    String schema();

    /**
     * Returns the unqualified table name reported by PostgreSQL.
     *
     * @return The database table name, preserving its spelling and case.
     */
    String table();

    /**
     * Returns the primary-key values before the change.
     *
     * @return Immutable values indexed by database column name; empty for inserts and table invalidations.
     */
    Map<String, String> oldKeys();

    /**
     * Returns the primary-key values after the change.
     *
     * @return Immutable values indexed by database column name; empty for deletes and table invalidations.
     */
    Map<String, String> newKeys();
}
