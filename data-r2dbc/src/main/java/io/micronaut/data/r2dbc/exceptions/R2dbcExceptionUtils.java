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
package io.micronaut.data.r2dbc.exceptions;

import io.micronaut.core.annotation.Internal;
import io.r2dbc.spi.R2dbcException;

import java.util.Locale;

/**
 * R2DBC exception classification utilities.
 *
 * @since 5.2.0
 */
@Internal
public final class R2dbcExceptionUtils {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private R2dbcExceptionUtils() {
    }

    /**
     * @param exception The R2DBC exception
     * @return Whether the exception represents a duplicate-key violation
     */
    public static boolean isUniqueConstraintViolation(R2dbcException exception) {
        if (UNIQUE_VIOLATION_SQL_STATE.equals(exception.getSqlState())) {
            return true;
        }
        int errorCode = exception.getErrorCode();
        if (errorCode == 1 || errorCode == 1062 || errorCode == 23505 || errorCode == 2601 || errorCode == 2627) {
            return true;
        }
        String message = exception.getMessage();
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
