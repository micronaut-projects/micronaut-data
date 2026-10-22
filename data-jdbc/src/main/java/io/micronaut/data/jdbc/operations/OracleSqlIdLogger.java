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
package io.micronaut.data.jdbc.operations;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.runtime.config.DataSettings;
import oracle.jdbc.OracleStatement;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Logs the Oracle SQL ID of a statement, so that a logged query can be matched with
 * {@code V$SQL}, AWR and SQL Monitor reports.
 *
 * @since 5.3.0
 */
@Internal
final class OracleSqlIdLogger {

    private static final boolean ORACLE_JDBC_PRESENT = ClassUtils.isPresent("oracle.jdbc.OracleStatement", OracleSqlIdLogger.class.getClassLoader());

    private OracleSqlIdLogger() {
    }

    /**
     * Logs the SQL ID of an Oracle statement at debug level of the query logger.
     *
     * @param statement The statement
     */
    static void log(Statement statement) {
        if (DataSettings.QUERY_LOG.isDebugEnabled()) {
            String sqlId = sqlId(statement);
            if (sqlId != null) {
                DataSettings.QUERY_LOG.debug("Oracle SQL ID: {}", sqlId);
            }
        }
    }

    /**
     * Resolves the SQL ID of an Oracle statement.
     *
     * @param statement The statement
     * @return The SQL ID, or {@code null} if the statement is not an Oracle statement
     */
    static @Nullable String sqlId(Statement statement) {
        if (!ORACLE_JDBC_PRESENT) {
            return null;
        }
        try {
            if (statement.isWrapperFor(OracleStatement.class)) {
                return statement.unwrap(OracleStatement.class).getSqlId();
            }
        } catch (SQLException | RuntimeException e) {
            // the SQL ID is diagnostic only, so a driver that cannot provide it is ignored
            return null;
        }
        return null;
    }
}
