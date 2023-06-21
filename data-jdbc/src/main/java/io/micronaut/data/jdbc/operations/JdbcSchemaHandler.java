/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.jdbc.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.sql.Connection;

/**
 * JDBC schema handler.
 *
 * @since 3.9.0
 * @author Denis Stepanov
 */
@Experimental
public interface JdbcSchemaHandler {

    /**
     * Creates a new schema.
     *
     * @param connection The JDBC connection
     * @param dialect The dialect
     * @param name The schema name
     */
    void createSchema(Connection connection, Dialect dialect, String name);

    /**
     * Uses the given schema..
     *
     * @param connection The JDBC connection
     * @param dialect The dialect
     * @param name The schema name
     */
    void useSchema(Connection connection, Dialect dialect, String name);

}
