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

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.R2dbcException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2dbcExceptionUtilsTest {

    @Test
    void recognizesCausedUniqueConstraintViolation() {
        R2dbcException exception = new R2dbcException(
            "batch update failed", "HY000", 0,
            new R2dbcDataIntegrityViolationException("duplicate key", "23505", 0)
        ) {
        };

        assertTrue(R2dbcExceptionUtils.isUniqueConstraintViolation(exception));
        assertTrue(R2dbcExceptionUtils.isIntegrityConstraintViolation(exception));
    }

    @Test
    void recognizesCausedNonUniqueIntegrityConstraintViolation() {
        R2dbcException exception = new R2dbcException(
            "batch update failed", "HY000", 0,
            new R2dbcDataIntegrityViolationException("not null violation", "23502", 0)
        ) {
        };

        assertFalse(R2dbcExceptionUtils.isUniqueConstraintViolation(exception));
        assertTrue(R2dbcExceptionUtils.isIntegrityConstraintViolation(exception));
    }

    @Test
    void recognizesSqlStateOnlyIntegrityConstraintViolation() {
        R2dbcException exception = new R2dbcException("foreign key violation", "23503", 0) {
        };

        assertTrue(R2dbcExceptionUtils.isIntegrityConstraintViolation(exception));
    }
}
