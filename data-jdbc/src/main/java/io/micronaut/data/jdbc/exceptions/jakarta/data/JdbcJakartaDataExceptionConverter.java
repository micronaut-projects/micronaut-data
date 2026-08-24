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
package io.micronaut.data.jdbc.exceptions.jakarta.data;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.DataIntegrityViolationException;
import io.micronaut.data.exceptions.EntityExistsException;
import io.micronaut.data.jdbc.exceptions.JdbcExceptionUtils;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataDeleteExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataInsertExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataUpdateExceptionConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;

/**
 * Converts JDBC exceptions to Micronaut Data exceptions before Jakarta Data conversion.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Internal
@Order(Ordered.HIGHEST_PRECEDENCE)
@Singleton
final class JdbcJakartaDataExceptionConverter implements JakartaDataExceptionConverter, JakartaDataUpdateExceptionConverter,
    JakartaDataDeleteExceptionConverter, JakartaDataInsertExceptionConverter {

    @Override
    public Exception convert(Exception exception) {
        if (exception instanceof EntityExistsException || exception instanceof DataIntegrityViolationException) {
            return exception;
        }
        SQLException sqlException = findSqlException(exception);
        if (sqlException == null) {
            return exception;
        }
        if (JdbcExceptionUtils.isUniqueConstraintViolation(sqlException)) {
            return new EntityExistsException("Entity already exists: " + sqlException.getMessage(), exception);
        }
        if (JdbcExceptionUtils.isIntegrityConstraintViolation(sqlException)) {
            return new DataIntegrityViolationException("Data integrity violation: " + sqlException.getMessage(), exception);
        }
        if (exception instanceof DataAccessException) {
            return exception;
        }
        return new DataAccessException("SQL error: " + sqlException.getMessage(), exception);
    }

    @Nullable
    private SQLException findSqlException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException;
            }
            cause = cause.getCause();
        }
        return null;
    }

}
