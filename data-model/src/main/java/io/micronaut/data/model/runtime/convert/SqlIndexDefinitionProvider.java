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
package io.micronaut.data.model.runtime.convert;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;

import java.util.function.Function;

/**
 * Extension interface to generate vendor-specific SQL index definitions for schema generation.
 * Implementations are standard Micronaut beans consulted by the schema generator.
 *
 * This mirrors {@link SqlColumnDefinitionProvider} but for index DDL.
 *
 * @since 5.0.0
 */
@Experimental
@Internal
public non-sealed interface SqlIndexDefinitionProvider extends DefinitionProvider {

    /**
     * Whether this provider supports the given property for the specified dialect.
     *
     * @param argument The persistent property argument
     * @param dialect The SQL dialect
     * @return true if supported
     */
     boolean supports(Argument<?> argument, Dialect dialect);

    /**
     * Produce the index DDL string for the provided mapping and context.
     *
     * @param indexName The index name (already escaped if needed)
     * @param escapedTableName The table name (already escaped if needed)
     * @param columns The index column names (unescaped)
     * @param escape Whether columns should be quoted/escaped
     * @param quoter A function to quote an identifier when {@code escape} is true
     * @param mapping The index mapping
     * @param dialect The SQL dialect
     * @return The CREATE INDEX... DDL string
     */
    String getIndexDefinition(String indexName,
                              String escapedTableName,
                              String[] columns,
                              boolean escape,
                              Function<String, String> quoter,
                              SqlIndexMapping mapping,
                              Dialect dialect);
}
