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
import io.micronaut.data.exceptions.EntityExistsException;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataDeleteExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataInsertExceptionConverter;
import io.micronaut.data.runtime.support.exceptions.jakarta.data.JakartaDataUpdateExceptionConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.Locale;

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

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    @Override
    public Exception convert(Exception exception) {
        if (exception instanceof EntityExistsException) {
            return exception;
        }
        SQLException sqlException = findSqlException(exception);
        if (sqlException == null) {
            return exception;
        }
        if (isUniqueConstraintViolation(sqlException)) {
            return new EntityExistsException("Entity already exists: " + sqlException.getMessage(), exception);
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

    private boolean isUniqueConstraintViolation(SQLException sqlException) {
        SQLException exception = sqlException;
        while (exception != null) {
            if (isUniqueConstraintViolation(exception.getSQLState(), exception.getErrorCode(), exception.getMessage())) {
                return true;
            }
            exception = exception.getNextException();
        }
        return false;
    }

    private boolean isUniqueConstraintViolation(@Nullable String sqlState, int errorCode, @Nullable String message) {
        if (UNIQUE_VIOLATION_SQL_STATE.equals(sqlState)) {
            return true;
        }
        if (errorCode == 1 || errorCode == 1062 || errorCode == 23505 || errorCode == 2601 || errorCode == 2627) {
            return true;
        }
        if (message != null) {
            String normalized = message.toLowerCase(Locale.ENGLISH);
            return normalized.contains("unique index")
                || normalized.contains("unique constraint")
                || normalized.contains("primary key violation")
                || normalized.contains("duplicate key")
                || normalized.contains("duplicate entry");
        }
        return false;
    }
}
