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
package io.micronaut.data.connection.annotation;

import io.micronaut.core.annotation.Experimental;
import java.lang.annotation.*;

/**
 * Oracle-only transaction priority hint.
 * <p>
 * When effective (Oracle Database 26ai+ with system wait targets configured),
 * a session-level transaction priority will be set for the duration of the transaction.
 * If a lower-priority transaction blocks a higher-priority one on row locks,
 * the database may automatically roll back the blocking lower-priority transaction
 * according to the configured system wait targets.
 * <p>
 * Notes:
 * - This annotation is a no-op for non-Oracle databases.
 * - It is applied at the beginning of a JDBC or R2DBC transaction and reset afterwards.
 * - It is orthogonal to propagation/isolation/readOnly. It does not change transactional semantics.
 * - Requires appropriate database configuration (PRIORITY_TXNS_* parameters).
 *
 * Usage:
 *  &#64;TransactionPriority(TransactionPriority.Level.HIGH)
 *  &#64;Transactional
 *  void criticalUpdate(...);
 *
 *  &#64;TransactionPriority(TransactionPriority.Level.LOW)
 *  void backgroundMaintenance(...);
 *
 * The default Oracle transaction priority is effectively HIGH if not specified.
 *
 * @author radovanradic
 * @since 5.10
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Experimental
public @interface TransactionPriority {

    /**
     * Priority level for Oracle priority transactions.
     */
    enum Level {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * The desired transaction priority level for Oracle priority transactions.
     *
     * @return The non-null priority level to set for the session (applied via ALTER SESSION "txn_priority").
     */
    Level value() default Level.HIGH;
}
