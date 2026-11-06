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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Immutable metadata captured from an Oracle row notification.
 *
 * @param rowId  The reported ROWID
 * @param schema The reported schema, or {@code null} for an unqualified table name
 * @param table  The reported unqualified table name
 */
@Internal
record DefaultOracleChangeEventMetadata(String rowId,
                                        @Nullable String schema,
                                        String table) implements OracleChangeEventMetadata {

    /**
     * Splits the driver's raw {@code SCHEMA.TABLE} name at the first separator. The remaining
     * table name can contain literal dots or quotes and is not a quoted SQL identifier.
     *
     * @param rowId             the reported ROWID
     * @param reportedTableName the table name supplied by the notification
     * @return the captured metadata
     */
    static DefaultOracleChangeEventMetadata from(String rowId, String reportedTableName) {
        int separator = reportedTableName.indexOf('.');
        String schema = separator < 0 ? null : reportedTableName.substring(0, separator);
        return new DefaultOracleChangeEventMetadata(rowId, schema, reportedTableName.substring(separator + 1));
    }
}
