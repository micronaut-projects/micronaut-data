/*
 * Copyright 2017-2021 original authors
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

import org.jspecify.annotations.NonNull;
import io.micronaut.core.convert.ConversionContext;

/**
 * Database-type-aware {@link ConversionContext} used by SQL mappers and converters.
 *
 * <p>Implementations provided by datastore modules (e.g. JDBC, R2DBC) expose the current
 * {@link DatabaseType} so that converters can render
 * vendor-specific behavior (types, column definitions, reading strategies).</p>
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
public interface DatabaseTypeConversionContext extends ConversionContext {

    /**
     * Returns the {@link DatabaseType} for the current operation.
     *
     * <p>Datastore modules (JDBC/R2DBC) provide non-null database type values.</p>
     *
     * @return the database type (never null)
     */
    @NonNull
    DatabaseType getDatabaseType();

}
