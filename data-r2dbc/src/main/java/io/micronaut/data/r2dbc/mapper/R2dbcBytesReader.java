/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.r2dbc.mapper;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.r2dbc.spi.Blob;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

/**
 * Utility for safely converting R2DBC values to byte arrays across different drivers.
 */
final class R2dbcBytesReader {

    private R2dbcBytesReader() {
    }

    static byte @Nullable [] toBytes(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof ByteBuffer byteBuffer) {
            ByteBuffer dup = byteBuffer.duplicate();
            byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            return bytes;
        }
        if (value instanceof Blob blob) {
            ByteBuffer byteBuffer = Mono.from(blob.stream()).block();
            if (byteBuffer == null) {
                return new byte[0];
            }
            ByteBuffer dup = byteBuffer.duplicate();
            byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            return bytes;
        }
        return conversionService.convert(value, byte[].class)
            .orElseThrow(() -> new DataAccessException("Cannot convert type [" + value.getClass() + "] with value [" + value + "] to target type: byte[]"));
    }
}
