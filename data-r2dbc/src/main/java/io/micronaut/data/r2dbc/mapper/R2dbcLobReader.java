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
package io.micronaut.data.r2dbc.mapper;

import io.r2dbc.spi.Blob;
import io.r2dbc.spi.Clob;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reads the whole content of an R2DBC {@link Clob} or {@link Blob}.
 *
 * <p>A large value is streamed in more than one chunk, so every chunk is read. Consuming the stream to the end
 * releases the LOB; if the read is cancelled before that, the LOB is discarded, as the R2DBC specification asks for
 * a stream that is not consumed.</p>
 */
final class R2dbcLobReader {

    private R2dbcLobReader() {
    }

    /**
     * @param clob The CLOB
     * @return The whole text, empty if the stream emits no chunk
     */
    static Mono<String> readClob(Clob clob) {
        // Copy each chunk when it is emitted, the driver may reuse it afterwards
        return readChunks(clob.stream(), clob::discard, CharSequence::toString)
            .flatMap(chunks -> {
                if (chunks.isEmpty()) {
                    return Mono.empty();
                }
                if (chunks.size() == 1) {
                    return Mono.just(chunks.getFirst());
                }
                return Mono.just(String.join("", chunks));
            });
    }

    /**
     * @param blob The BLOB
     * @return The whole content, an empty array if the stream emits no chunk
     */
    static Mono<byte[]> readBlob(Blob blob) {
        // Copy each chunk when it is emitted, the driver may reuse it afterwards
        return readChunks(blob.stream(), blob::discard, R2dbcLobReader::toBytes)
            .map(chunks -> {
                if (chunks.size() == 1) {
                    return chunks.getFirst();
                }
                int length = 0;
                for (byte[] chunk : chunks) {
                    length += chunk.length;
                }
                byte[] bytes = new byte[length];
                int offset = 0;
                for (byte[] chunk : chunks) {
                    System.arraycopy(chunk, 0, bytes, offset, chunk.length);
                    offset += chunk.length;
                }
                return bytes;
            });
    }

    /**
     * Copies the remaining bytes of the buffer, without changing its position. Unlike {@link ByteBuffer#array()}
     * this honours the position and limit, and works for a direct or read-only buffer.
     *
     * @param byteBuffer The buffer
     * @return The remaining bytes
     */
    static byte[] toBytes(ByteBuffer byteBuffer) {
        ByteBuffer duplicate = byteBuffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private static <C, T> Mono<List<T>> readChunks(Publisher<C> stream,
                                                   Supplier<Publisher<Void>> discard,
                                                   Function<C, T> copy) {
        return Flux.from(stream)
            .map(copy)
            .doOnCancel(() -> Mono.from(discard.get()).onErrorResume(_ -> Mono.empty()).subscribe())
            .collectList();
    }
}
