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

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Base64;
import java.util.Objects;

/**
 * Default Oracle sessionless transaction id codec.
 */
@Singleton
@Requires(missingBeans = OracleSessionlessTransactionIdCodec.class)
final class DefaultOracleSessionlessTransactionIdCodec implements OracleSessionlessTransactionIdCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    @Override
    public String encode(byte[] gtrid) {
        byte[] value = Objects.requireNonNull(gtrid, "gtrid");
        if (value.length == 0) {
            throw new IllegalArgumentException("Oracle sessionless transaction id cannot be empty");
        }
        return ENCODER.encodeToString(value);
    }

    @Override
    public byte[] decode(String encodedTransactionId) {
        byte[] decoded = DECODER.decode(Objects.requireNonNull(encodedTransactionId, "encodedTransactionId"));
        if (decoded.length == 0) {
            throw new IllegalArgumentException("Oracle sessionless transaction id cannot be empty");
        }
        return decoded;
    }
}
