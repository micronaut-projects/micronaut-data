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

import org.junit.jupiter.api.Test;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcExceptionUtilsTest {

    @Test
    void recognizesChainedUniqueConstraintViolation() {
        BatchUpdateException batchException = new BatchUpdateException("batch update failed", "HY000", 0, new int[0]);
        batchException.setNextException(new SQLIntegrityConstraintViolationException("duplicate key", "23505", 0));

        assertTrue(JdbcExceptionUtils.isUniqueConstraintViolation(batchException));
        assertTrue(JdbcExceptionUtils.isIntegrityConstraintViolation(batchException));
    }

    @Test
    void recognizesChainedNonUniqueIntegrityConstraintViolation() {
        BatchUpdateException batchException = new BatchUpdateException("batch update failed", "HY000", 0, new int[0]);
        batchException.setNextException(new SQLIntegrityConstraintViolationException("not null violation", "23502", 0));

        assertFalse(JdbcExceptionUtils.isUniqueConstraintViolation(batchException));
        assertTrue(JdbcExceptionUtils.isIntegrityConstraintViolation(batchException));
    }

    @Test
    void recognizesSqlStateOnlyIntegrityConstraintViolation() {
        assertTrue(JdbcExceptionUtils.isIntegrityConstraintViolation(new SQLException("foreign key violation", "23503", 0)));
        assertTrue(JdbcExceptionUtils.isIntegrityConstraintViolation(new SQLException("integrity violation", "23000", 0)));
    }
}
