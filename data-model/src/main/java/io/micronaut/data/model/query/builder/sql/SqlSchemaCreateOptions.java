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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.Experimental;

/**
 * The options of the {@code CREATE TABLE} statements built by {@link SqlQueryBuilder}, the optional schema objects
 * to create with the tables. The default options create only the tables, their sequences and declared indexes.
 *
 * @param uniqueConstraints Whether to create the JPA unique constraints ({@code @Column(unique = true)} and
 * {@code @Table(uniqueConstraints = ...)}) as unique indexes
 * @author radovanradic
 * @since 5.3.0
 */
@Experimental
public record SqlSchemaCreateOptions(boolean uniqueConstraints) {

    /**
     * The default options.
     */
    public static final SqlSchemaCreateOptions DEFAULT = new SqlSchemaCreateOptions(false);

    /**
     * @param uniqueConstraints Whether to create the JPA unique constraints
     * @return The options with the given unique constraints option
     */
    public SqlSchemaCreateOptions withUniqueConstraints(boolean uniqueConstraints) {
        return new SqlSchemaCreateOptions(uniqueConstraints);
    }
}
