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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.r2dbc.spi.Connection;
import org.reactivestreams.Publisher;

/**
 * The schema handler for R2DBC.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Experimental
public interface R2dbcSchemaHandler {

    /**
     * Creates a new schema.
     *
     * @param connection The R2DBC connection
     * @param dialect    The dialect
     * @param name       The schema name
     *
     * @return The publisher
     */
    Publisher<Void> createSchema(Connection connection, Dialect dialect, String name);

    /**
     * Uses the given schema..
     *
     * @param connection The R2DBC connection
     * @param dialect    The dialect
     * @param name       The schema name
     *
     * @return The publisher
     */
    Publisher<Void> useSchema(Connection connection, Dialect dialect, String name);

}
