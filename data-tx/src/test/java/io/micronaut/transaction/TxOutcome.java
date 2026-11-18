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
package io.micronaut.transaction;

import java.util.List;

/**
 * What a transactional scope did, as seen by the propagation tests.
 *
 * @param newTransaction The value of {@code isNewTransaction} seen by the work, {@code null} if the scope was rejected
 * @param connection     The connection seen by the work, {@code null} if the scope was rejected
 * @param failure        The type of the failure that rejected the scope
 * @param log            The connection and transaction operations
 */
record TxOutcome(Boolean newTransaction, String connection, Class<?> failure, List<String> log) {

    static TxOutcome of(boolean newTransaction, String connection) {
        return new TxOutcome(newTransaction, connection, null, List.of());
    }

    static TxOutcome of(boolean newTransaction, String connection, List<String> log) {
        return new TxOutcome(newTransaction, connection, null, log);
    }

    static TxOutcome failed(Throwable failure) {
        return new TxOutcome(null, null, failure.getClass(), List.of());
    }

    static TxOutcome failed(Class<?> failure, List<String> log) {
        return new TxOutcome(null, null, failure, log);
    }

    TxOutcome withLog(List<String> newLog) {
        return new TxOutcome(newTransaction, connection, failure, List.copyOf(newLog));
    }
}
