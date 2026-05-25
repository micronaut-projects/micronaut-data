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

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Propagated Oracle sessionless transaction state.
 */
final class OracleSessionlessTransactionState implements PropagatedContextElement {

    private byte @Nullable [] gtrid;

    Optional<byte[]> getGtrid() {
        return Optional.ofNullable(gtrid).map(byte[]::clone);
    }

    void setGtrid(byte[] gtrid) {
        this.gtrid = copy(gtrid);
    }

    boolean setGtridIfAbsent(byte[] gtrid) {
        if (this.gtrid != null) {
            return false;
        }
        this.gtrid = copy(gtrid);
        return true;
    }

    void clearGtrid() {
        gtrid = null;
    }

    static Optional<OracleSessionlessTransactionState> current() {
        return find(PropagatedContext.getOrEmpty());
    }

    static Optional<OracleSessionlessTransactionState> find(PropagatedContext context) {
        return context.findAll(OracleSessionlessTransactionState.class).findFirst();
    }

    static PropagatedContext withoutExisting(PropagatedContext context) {
        PropagatedContext current = context;
        for (OracleSessionlessTransactionState element : context.findAll(OracleSessionlessTransactionState.class).toList()) {
            current = current.minus(element);
        }
        return current;
    }

    private static byte[] copy(byte[] gtrid) {
        return Objects.requireNonNull(gtrid, "gtrid").clone();
    }
}
