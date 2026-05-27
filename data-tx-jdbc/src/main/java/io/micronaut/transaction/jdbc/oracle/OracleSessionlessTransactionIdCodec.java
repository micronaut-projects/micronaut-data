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

import io.micronaut.core.annotation.Experimental;

/**
 * Converts Oracle sessionless transaction identifiers between the JDBC binary representation and
 * an external string representation suitable for transport propagation.
 *
 * <p>Applications can provide their own bean implementation to apply additional protection, for
 * example signing or encrypting the encoded value before it is exposed over HTTP.</p>
 *
 * @since 5.1.0
 */
@Experimental
public interface OracleSessionlessTransactionIdCodec {

    /**
     * Encodes a non-empty Oracle sessionless transaction identifier.
     *
     * @param gtrid The Oracle global transaction identifier
     * @return The encoded transaction identifier
     * @throws IllegalArgumentException If the identifier cannot be encoded
     */
    String encode(byte[] gtrid);

    /**
     * Decodes an encoded Oracle sessionless transaction identifier.
     *
     * @param encodedTransactionId The encoded transaction identifier
     * @return The Oracle global transaction identifier
     * @throws IllegalArgumentException If the value cannot be decoded
     */
    byte[] decode(String encodedTransactionId);
}
