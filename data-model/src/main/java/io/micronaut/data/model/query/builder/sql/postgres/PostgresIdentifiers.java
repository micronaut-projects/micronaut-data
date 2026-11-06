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
package io.micronaut.data.model.query.builder.sql.postgres;

import io.micronaut.core.annotation.Internal;

import java.nio.charset.StandardCharsets;

/**
 * Normalizes mapped identifiers for PostgreSQL databases using UTF-8 and the default identifier limit.
 */
@Internal
public final class PostgresIdentifiers {
    private static final int MAX_IDENTIFIER_BYTES = 63;

    private PostgresIdentifiers() {
    }

    /**
     * Resolves the catalog spelling of a mapped identifier. PostgreSQL folds only ASCII uppercase
     * letters in unquoted UTF-8 identifiers and truncates both quoted and unquoted identifiers
     * to 63 bytes, preserving complete characters.
     *
     * @param identifier the mapped name without SQL identifier quotes
     * @param quoted whether SQL generation quotes the identifier
     * @return the identifier spelling used in notification payloads
     */
    public static String normalize(String identifier, boolean quoted) {
        byte[] bytes = identifier.getBytes(StandardCharsets.UTF_8);
        if (!quoted) {
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] >= 'A' && bytes[i] <= 'Z') {
                    bytes[i] = (byte) (bytes[i] + ('a' - 'A'));
                }
            }
        }
        int length = Math.min(bytes.length, MAX_IDENTIFIER_BYTES);
        if (length < bytes.length) {
            // Back up over continuation bytes when the byte limit falls inside a UTF-8 character.
            while (length > 0 && (bytes[length] & 0xC0) == 0x80) {
                length--;
            }
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }
}
