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
package io.micronaut.data.r2dbc.exceptions.jakarta.data;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.DataIntegrityViolationException;
import io.micronaut.data.exceptions.EntityExistsException;
import io.micronaut.data.r2dbc.exceptions.R2dbcExceptionUtils;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataDeleteExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataInsertExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataUpdateExceptionConverter;
import io.r2dbc.spi.R2dbcException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;


/**
 * Converts R2DBC exceptions to Micronaut Data exceptions before Jakarta Data conversion.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Internal
@Order(Ordered.HIGHEST_PRECEDENCE)
@Singleton
final class R2dbcJakartaDataExceptionConverter implements JakartaDataExceptionConverter, JakartaDataUpdateExceptionConverter,
    JakartaDataDeleteExceptionConverter, JakartaDataInsertExceptionConverter {

    @Override
    public Exception convert(Exception exception) {
        if (exception instanceof EntityExistsException || exception instanceof DataIntegrityViolationException) {
            return exception;
        }
        R2dbcException r2dbcException = findR2dbcException(exception);
        if (r2dbcException == null) {
            return exception;
        }
        if (R2dbcExceptionUtils.isUniqueConstraintViolation(r2dbcException)) {
            return new EntityExistsException("Entity already exists: " + r2dbcException.getMessage(), exception);
        }
        if (R2dbcExceptionUtils.isIntegrityConstraintViolation(r2dbcException)) {
            return new DataIntegrityViolationException("Data integrity violation: " + r2dbcException.getMessage(), exception);
        }
        if (exception instanceof DataAccessException) {
            return exception;
        }
        return new DataAccessException("SQL error: " + r2dbcException.getMessage(), exception);
    }

    @Nullable
    private R2dbcException findR2dbcException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof R2dbcException r2dbcException) {
                return r2dbcException;
            }
            cause = cause.getCause();
        }
        return null;
    }

}
