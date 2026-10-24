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
package io.micronaut.data.jdbc.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.model.runtime.convert.DataAccessType;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext;
import io.micronaut.data.runtime.convert.ArgumentDatabaseTypeConversionContext;
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;

/**
 * JDBC {@link DatabaseConversionContextFactory} that provides argument conversion contexts
 * containing database type metadata without requiring a live JDBC connection.
 */
@Internal
@EachBean(DataSource.class)
final class JdbcDatabaseConversionContextFactory implements DatabaseConversionContextFactory {

    private final DatabaseType databaseType;

    /**
     * @param jdbcConfiguration JDBC configuration used to resolve the configured dialect
     */
    JdbcDatabaseConversionContextFactory(@Parameter DataJdbcConfiguration jdbcConfiguration) {
        this.databaseType = DatabaseType.from(jdbcConfiguration.getDialect());
    }

    /**
     * @param argument argument metadata
     * @return database-type-aware argument conversion context
     */
    @Override
    @NonNull
    public DatabaseTypeConversionContext forArgument(@NonNull Argument<?> argument) {
        return new ArgumentDatabaseTypeConversionContext(databaseType, getDataAccessType(), argument);
    }

    @Override
    public DataAccessType getDataAccessType() {
        return DataAccessType.JDBC;
    }
}
