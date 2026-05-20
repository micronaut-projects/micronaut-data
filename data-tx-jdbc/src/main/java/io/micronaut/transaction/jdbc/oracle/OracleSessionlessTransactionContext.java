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

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Propagated Oracle sessionless transaction state.
 */
final class OracleSessionlessTransactionContext implements PropagatedContextElement {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] gtrid;

    OracleSessionlessTransactionContext(byte[] gtrid) {
        this.gtrid = Objects.requireNonNull(gtrid, "gtrid").clone();
    }

    byte[] gtrid() {
        return gtrid.clone();
    }

    String encode() {
        return ENCODER.encodeToString(gtrid);
    }

    static OracleSessionlessTransactionContext decode(String value) {
        byte[] decoded = DECODER.decode(value);
        if (decoded.length == 0) {
            throw new IllegalArgumentException("Oracle sessionless transaction id cannot be empty");
        }
        return new OracleSessionlessTransactionContext(decoded);
    }

    static Optional<OracleSessionlessTransactionContext> find() {
        return find(PropagatedContext.getOrEmpty());
    }

    static Optional<OracleSessionlessTransactionContext> find(PropagatedContext context) {
        return context.findAll(OracleSessionlessTransactionContext.class).findFirst();
    }

    static PropagatedContext withoutExisting(PropagatedContext context) {
        PropagatedContext current = context;
        for (OracleSessionlessTransactionContext element : context.findAll(OracleSessionlessTransactionContext.class).toList()) {
            current = current.minus(element);
        }
        return current;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OracleSessionlessTransactionContext that)) {
            return false;
        }
        return Arrays.equals(gtrid, that.gtrid);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(gtrid);
    }
}
