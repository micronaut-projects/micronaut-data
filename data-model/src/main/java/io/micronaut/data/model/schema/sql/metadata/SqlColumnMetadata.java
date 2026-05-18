/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.schema.sql.metadata;

import io.micronaut.core.annotation.Internal;

/**
 * SQL column metadata extracted from the underlying table column in the database.
 *
 * @param name The column name
 * @param type The column type code (corresponds with {@link java.sql.Types})
 * @param typeName The column type name
 * @param columnSize The column size
 * @param decimalDigits The number of decimal digits for numeric columns
 * @param nullable The indicator telling whether column is nullable
 */
@Internal
public record SqlColumnMetadata(String name,
    int type,
    String typeName,
    int columnSize,
    int decimalDigits,
    boolean nullable) {
}
