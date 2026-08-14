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

import io.micronaut.data.jdbc.notification.ChangeEventMetadata;

/**
 * Oracle-specific metadata for a row-level change event.
 *
 * @param rowId The Oracle {@code ROWID} reported for the changed row.
 * @since 5.2.0
 */
public record OracleChangeEventMetadata(String rowId) implements ChangeEventMetadata {
}
