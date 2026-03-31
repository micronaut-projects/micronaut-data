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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory;
import io.micronaut.data.runtime.support.AbstractConversionContext;
import io.r2dbc.spi.ConnectionFactory;

/**
 * R2DBC {@link DatabaseConversionContextFactory} that provides argument conversion contexts
 * containing database type metadata without requiring a live R2DBC connection.
 */
@Internal
@EachBean(ConnectionFactory.class)
final class R2dbcDatabaseConversionContextFactory implements DatabaseConversionContextFactory {

    private final DatabaseType databaseType;

    /**
     * @param configuration R2DBC configuration used to resolve the configured dialect
     */
    R2dbcDatabaseConversionContextFactory(@Parameter DataR2dbcConfiguration configuration) {
        this.databaseType = DatabaseType.from(configuration.getDialect());
    }

    /**
     * @param argument argument metadata
     * @return database-type-aware argument conversion context
     */
    @Override
    public DatabaseTypeConversionContext forArgument(Argument<?> argument) {
        return new ArgumentDatabaseTypeContext(databaseType, argument);
    }

    /**
     * Lightweight conversion context carrying only argument metadata and database type.
     */
    private static final class ArgumentDatabaseTypeContext extends AbstractConversionContext
        implements DatabaseTypeConversionContext, ArgumentConversionContext<Object> {

        private final DatabaseType databaseType;
        private final Argument<Object> argument;

        /**
         * @param databaseType database type
         * @param argument argument metadata
         */
        @SuppressWarnings("unchecked")
        private ArgumentDatabaseTypeContext(DatabaseType databaseType, Argument<?> argument) {
            super(ConversionContext.of(argument));
            this.databaseType = databaseType;
            this.argument = (Argument<Object>) argument;
        }

        @Override
        public DatabaseType getDatabaseType() {
            return databaseType;
        }

        @Override
        public Argument<Object> getArgument() {
            return argument;
        }
    }
}
