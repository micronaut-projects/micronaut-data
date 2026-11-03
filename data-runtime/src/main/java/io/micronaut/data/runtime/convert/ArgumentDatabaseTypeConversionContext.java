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
package io.micronaut.data.runtime.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.convert.DataAccessType;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext;
import io.micronaut.data.runtime.support.AbstractConversionContext;
import org.jspecify.annotations.NonNull;

/**
 * Lightweight conversion context carrying only argument metadata, the database type and the driver API, for
 * {@link DatabaseConversionContextFactory} implementations that need no live connection.
 *
 * @since 5.3.0
 */
@Internal
public final class ArgumentDatabaseTypeConversionContext extends AbstractConversionContext
    implements DatabaseTypeConversionContext, ArgumentConversionContext<Object> {

    private final DatabaseType databaseType;
    private final DataAccessType dataAccessType;
    private final Argument<Object> argument;

    /**
     * @param databaseType database type
     * @param dataAccessType the driver API of the factory
     * @param argument argument metadata
     */
    @SuppressWarnings("unchecked")
    public ArgumentDatabaseTypeConversionContext(DatabaseType databaseType, DataAccessType dataAccessType, Argument<?> argument) {
        super(ConversionContext.of(argument));
        this.databaseType = databaseType;
        this.dataAccessType = dataAccessType;
        this.argument = (Argument<Object>) argument;
    }

    /**
     * @return resolved database type
     */
    @Override
    @NonNull
    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    /**
     * @return the driver API of the factory
     */
    @Override
    public DataAccessType getDataAccessType() {
        return dataAccessType;
    }

    /**
     * @return conversion argument metadata
     */
    @Override
    @NonNull
    public Argument<Object> getArgument() {
        return argument;
    }

    /**
     * @return argument type parameters
     */
    @Override
    public Argument<?> @NonNull [] getTypeParameters() {
        return argument.getTypeParameters();
    }
}
