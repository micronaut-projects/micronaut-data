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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.r2dbc.spi.R2dbcException;

/**
 * The {@link io.r2dbc.spi.R2dbcException} mapper interface. Can be used to map given R2dbc exceptions to some custom exceptions
 * (for example {@link DataAccessException} and its descendents like {@link io.micronaut.data.exceptions.OptimisticLockException}).
 *
 * @since 4.8.0
 */
@Experimental
public interface R2dbcExceptionMapper extends Ordered {

    /**
     * @return the {@link Dialect} that this mapper supports
     */
    @NonNull
    Dialect getDialect();

    /**
     * Maps {@link io.r2dbc.spi.R2dbcException} to custom {@link DataAccessException}.
     * In case when mapper is not able to map {@link io.r2dbc.spi.R2dbcException} to custom {@link DataAccessException} then result will be null
     * indicating that mapper cannot map the exception.
     *
     * @param r2dbcException The R2dbc exception
     * @return mapped {@link DataAccessException} from {@link R2dbcException} or if mapper cannot map {@link R2dbcException} then returns null
     */
    @Nullable
    DataAccessException mapR2dbcException(@NonNull R2dbcException r2dbcException);
}
