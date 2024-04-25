/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.sql.SQLException;

/**
 * The {@link SQLException} mapper interface. Can be used to map given SQL exceptions to some custom exceptions
 * (for example {@link DataAccessException} and its descendents like {@link io.micronaut.data.exceptions.OptimisticLockException}).
 * Only one {@link SqlExceptionMapper} should be present for one dialect.
 */
@Internal
@Experimental
interface SqlExceptionMapper {

    /**
     * @return the {@link Dialect} that this mapper supports
     */
    @NonNull
    Dialect getDialect();

    /**
     * Maps {@link SQLException} to custom {@link DataAccessException}.
     * In case when mapper is not able to map {@link SQLException} to custom {@link DataAccessException} then result will be null
     * indicating that mapper cannot map the exception.
     *
     * @param sqlException The SQL exception
     * @return mapped {@link DataAccessException} from {@link SQLException} or if mapper cannot map {@link SQLException} then returns null
     */
    @Nullable
    DataAccessException mapSqlException(@NonNull SQLException sqlException);
}
