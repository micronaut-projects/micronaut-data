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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.notification.ChangeEventMetadata;

import java.util.Optional;

/**
 * Oracle-specific metadata for a row-level Continuous Query Notification event.
 *
 * <p>Instances are supplied by the Oracle notification provider. The reported table identifiers
 * preserve Oracle's spelling and case without SQL identifier quoting.</p>
 *
 * <p>A {@code ROWID} is a row-location hint, not a stable entity identifier. Oracle can reuse it
 * after a committed delete or change it during row movement. A deferred reload can therefore
 * return a different row or no row. When identity matters, compare the reloaded primary key with
 * an independently known expected id, or reconcile the registered data. The notification does
 * not provide the original primary key.</p>
 *
 * @since 5.3.0
 */
@Experimental
public interface OracleChangeEventMetadata extends ChangeEventMetadata {

    /**
     * Returns the row-location hint supplied by Oracle Database for this notification.
     *
     * @return The reported Oracle {@code ROWID}; it may be reused or changed before entity reload.
     */
    String rowId();

    /**
     * Returns the schema (table owner) reported by Oracle Database.
     *
     * @return The reported schema, or empty when the notification supplies an unqualified table name.
     */
    Optional<String> schema();

    /**
     * Returns the unqualified table name reported by Oracle Database.
     *
     * @return The reported table name, preserving its case and literal identifier characters.
     */
    String table();
}
