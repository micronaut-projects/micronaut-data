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
package io.micronaut.transaction.exceptions;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleTransactionPriorityExceptionTest {

    @Test
    void recognizesAutomaticPriorityRollback() {
        assertTrue(OracleTransactionPriorityException.isPriorityRollback(
            new SQLException("ORA-63300", "99999", OracleTransactionPriorityException.ORA_TRANSACTION_AUTOMATICALLY_ROLLED_BACK)
        ));
    }

    @Test
    void recognizesRollbackAcknowledgementError() {
        assertTrue(OracleTransactionPriorityException.isPriorityRollback(
            new SQLException("ORA-63302", "99999", OracleTransactionPriorityException.ORA_TRANSACTION_MUST_ROLLBACK)
        ));
    }

    @Test
    void recognizesOracleErrorCodeInMessageWhenDriverDoesNotExposeIt() {
        assertTrue(OracleTransactionPriorityException.isPriorityRollback(
            new SQLException("ORA-63300: transaction was automatically rolled back")
        ));
    }

    @Test
    void recognizesNestedAndChainedSqlExceptions() throws SQLException {
        SQLException chained = new SQLException("ORA-63302", "99999",
            OracleTransactionPriorityException.ORA_TRANSACTION_MUST_ROLLBACK);
        SQLException driverException = new SQLException("driver error");
        driverException.setNextException(chained);

        assertTrue(OracleTransactionPriorityException.isPriorityRollback(
            new RuntimeException("repository error", driverException)
        ));
    }

    @Test
    void ignoresOtherOracleErrors() {
        assertFalse(OracleTransactionPriorityException.isPriorityRollback(
            new SQLException("ORA-02248", "99999", 2248)
        ));
    }
}
