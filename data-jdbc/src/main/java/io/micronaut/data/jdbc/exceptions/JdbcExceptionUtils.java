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
package io.micronaut.data.jdbc.exceptions;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Locale;

/**
 * JDBC exception classification utilities.
 *
 * @since 5.2.0
 */
@Internal
public final class JdbcExceptionUtils {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String INTEGRITY_VIOLATION_SQL_STATE_PREFIX = "23";

    private JdbcExceptionUtils() {
    }

    /**
     * @param sqlException The SQL exception
     * @return Whether the exception or one of its chained exceptions represents a duplicate-key violation
     */
    public static boolean isUniqueConstraintViolation(SQLException sqlException) {
        SQLException exception = sqlException;
        while (exception != null) {
            if (isUniqueConstraintViolation(exception.getSQLState(), exception.getErrorCode(), exception.getMessage())) {
                return true;
            }
            exception = exception.getNextException();
        }
        return false;
    }

    /**
     * @param sqlException The SQL exception
     * @return Whether the exception or one of its chained exceptions represents an integrity constraint violation
     */
    public static boolean isIntegrityConstraintViolation(SQLException sqlException) {
        SQLException exception = sqlException;
        while (exception != null) {
            if (exception instanceof SQLIntegrityConstraintViolationException
                || isIntegrityConstraintSqlState(exception.getSQLState())) {
                return true;
            }
            exception = exception.getNextException();
        }
        return false;
    }

    private static boolean isIntegrityConstraintSqlState(@Nullable String sqlState) {
        return sqlState != null && sqlState.startsWith(INTEGRITY_VIOLATION_SQL_STATE_PREFIX);
    }

    private static boolean isUniqueConstraintViolation(@Nullable String sqlState, int errorCode, @Nullable String message) {
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
