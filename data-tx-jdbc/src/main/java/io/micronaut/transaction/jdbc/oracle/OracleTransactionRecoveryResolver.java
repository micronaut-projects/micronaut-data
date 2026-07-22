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
package io.micronaut.transaction.jdbc.oracle;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.recovery.CommitOutcome;
import io.micronaut.transaction.recovery.CommitOutcomeResolver;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLRecoverableException;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Types;

/**
 * Oracle transaction recovery resolver backed by Oracle Transaction Guard.
 *
 * @since 5.2
 */
@Internal
@EachBean(DataSource.class)
@Requires(classes = OracleConnection.class)
@Requires(condition = OracleTransactionRecoveryCondition.class)
final class OracleTransactionRecoveryResolver implements CommitOutcomeResolver {

    private static final String GET_LTXID_OUTCOME =
        """
        DECLARE
              PROCEDURE get_ltxid_outcome_wrapper(p_ltxid IN RAW, p_committed OUT NUMBER, p_user_call_completed OUT NUMBER) IS
                committed BOOLEAN;
                user_call_completed BOOLEAN;
              BEGIN
                sys.dbms_app_cont.get_ltxid_outcome(p_ltxid, committed, user_call_completed);
                IF committed THEN p_committed := 1; ELSE p_committed := 0; END IF;
                IF user_call_completed THEN p_user_call_completed := 1; ELSE p_user_call_completed := 0; END IF;
              END;
        BEGIN
            get_ltxid_outcome_wrapper(?, ?, ?);
        END;
        """;

    private final DataSource dataSource;

    OracleTransactionRecoveryResolver(@NonNull @Parameter DataSource dataSource) {
        this.dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
    }

    @Override
    public @Nullable Object captureLtxid(@NonNull TransactionStatus<?> status) {
        OracleConnection oracleConnection = unwrapRequiredOracleConnection(status.getConnection());
        try {
            return oracleConnection.getLogicalTransactionId();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to capture Oracle logical transaction id", e);
        }
    }

    @Override
    public @NonNull CommitOutcome resolve(@NonNull Object token) {
        if (token instanceof CharSequence value && value.toString().trim().isEmpty()) {
            return CommitOutcome.UNKNOWN;
        }
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall(GET_LTXID_OUTCOME)) {
            bindToken(statement, token);
            statement.registerOutParameter(2, Types.INTEGER);
            statement.registerOutParameter(3, Types.INTEGER);
            statement.execute();
            return mapOutcome(statement.getObject(2), statement.getObject(3));
        } catch (SQLException e) {
            if (e instanceof SQLRecoverableException || e instanceof SQLTransientException) {
                return CommitOutcome.UNKNOWN;
            }
            throw new IllegalStateException("Oracle transaction recovery outcome resolution failed", e);
        }
    }

    private static void bindToken(@NonNull CallableStatement statement, @NonNull Object token) throws SQLException {
        if (token instanceof CharSequence value) {
            statement.setString(1, value.toString().trim());
            return;
        }
        statement.setObject(1, token);
    }

    @NonNull
    private static OracleConnection unwrapRequiredOracleConnection(@NonNull Object connection) {
        if (connection instanceof OracleConnection oracleConnection) {
            return oracleConnection;
        }
        if (connection instanceof Connection jdbcConnection) {
            try {
                OracleConnection oracleConnection = jdbcConnection.unwrap(OracleConnection.class);
                if (oracleConnection != null) {
                    return oracleConnection;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Oracle transaction recovery requires an unwrap-able Oracle JDBC connection", e);
            }
            throw new IllegalStateException("Oracle transaction recovery requires an unwrap-able Oracle JDBC connection");
        }
        throw new IllegalStateException("Oracle transaction recovery requires a JDBC Connection but got: " + connection.getClass().getName());
    }

    @NonNull
    private static CommitOutcome mapOutcome(@Nullable Object committed, @Nullable Object userCallCompleted) {
        Boolean committedValue = toBoolean(committed);
        if (committedValue == null) {
            return CommitOutcome.UNKNOWN;
        }
        if (!committedValue) {
            return CommitOutcome.NOT_COMMITTED;
        }
        Boolean userCallCompletedValue = toBoolean(userCallCompleted);
        if (userCallCompletedValue == null) {
            return CommitOutcome.UNKNOWN;
        }
        return userCallCompletedValue ? CommitOutcome.COMMITTED : CommitOutcome.COMMITTED_CALL_INCOMPLETE;
    }

    @Nullable
    private static Boolean toBoolean(@Nullable Object outcome) {
        if (outcome == null) {
            return null;
        }
        if (outcome instanceof Boolean value) {
            return value;
        }
        if (outcome instanceof Number value) {
            return value.intValue() != 0;
        }
        String value = outcome.toString().trim();
        if (CommitOutcome.COMMITTED.name().equalsIgnoreCase(value) || StringUtils.TRUE.equalsIgnoreCase(value) || "1".equals(value)) {
            return Boolean.TRUE;
        }
        if (CommitOutcome.NOT_COMMITTED.name().equalsIgnoreCase(value) || StringUtils.FALSE.equalsIgnoreCase(value) || "0".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
