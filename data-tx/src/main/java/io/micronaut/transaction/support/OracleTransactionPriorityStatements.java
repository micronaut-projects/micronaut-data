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
package io.micronaut.transaction.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.annotation.OracleTransactional;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The statements that set the Oracle transaction priority of a session, shared by the JDBC and R2DBC transaction managers.
 *
 * @since 5.3.0
 */
@Internal
public final class OracleTransactionPriorityStatements {

    /**
     * Sets the priority given by the first parameter and returns the previous priority of the session in the second,
     * an out parameter: {@code NULL} when it can't be read. Reading it needs the privilege to read {@code V$PARAMETER};
     * the query is dynamic, so that a missing privilege fails when the query runs, inside the exception handler,
     * not when the block is compiled.
     */
    public static final String SET_PRIORITY_READING_PREVIOUS = """
        DECLARE
          previous VARCHAR2(16);
        BEGIN
          BEGIN
            EXECUTE IMMEDIATE 'SELECT value FROM v$parameter WHERE name = ''txn_priority''' INTO previous;
          EXCEPTION
            WHEN OTHERS THEN
              previous := NULL;
          END;
          EXECUTE IMMEDIATE 'ALTER SESSION SET "txn_priority"="' || ? || '"';
          ? := previous;
        END;""";

    private OracleTransactionPriorityStatements() {
    }

    /**
     * @param priority The priority
     * @return The statement that sets the priority of the session
     */
    public static String setPriority(OracleTransactional.Priority priority) {
        return "ALTER SESSION SET \"txn_priority\"=\"" + priority.name() + "\"";
    }

    /**
     * @param value The priority read from the session
     * @return The priority, or {@code null} if the value is missing or isn't a known priority
     */
    public static OracleTransactional.@Nullable Priority parsePriority(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return OracleTransactional.Priority.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
