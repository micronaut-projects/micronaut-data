/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.r2dbc.h2

import io.micronaut.data.model.DataType
import io.micronaut.data.r2dbc.mapper.ColumnIndexR2dbcResultReader
import io.micronaut.data.r2dbc.mapper.ColumnNameR2dbcResultReader
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.r2dbc.spi.Blob
import io.r2dbc.spi.Row
import reactor.core.publisher.Flux
import spock.lang.Specification

import jakarta.inject.Inject
import java.nio.ByteBuffer

@MicronautTest(transactional = false)
class H2ByteArraySpec extends Specification implements H2TestPropertyProvider {

    @Inject
    H2BlobEntityRepository blobRepository

    // -------------------------------------------------------------------------
    // H2 integration tests: BlobEntity with an explicit BLOB column.
    // H2 R2DBC returns BLOB column values as ByteBuffer (via BlobToByteBufferCodec),
    // so these tests exercise the ByteBuffer fallback path in readBlob/readBytes.
    // -------------------------------------------------------------------------

    void 'test read BLOB column via column name reader (full entity fetch)'() {
        given:
        byte[] payload = [1, 2, 3, 4, 5] as byte[]
        BlobEntity saved = new BlobEntity(data: payload)
        saved = blobRepository.save(saved).block()

        when: 'full entity fetch exercises ColumnNameR2dbcResultReader.readBlob'
        BlobEntity retrieved = blobRepository.findById(saved.id).block()

        then:
        retrieved != null
        retrieved.data == payload

        cleanup:
        blobRepository.deleteById(saved.id).block()
    }

    void 'test read BLOB column via column index reader (single column query)'() {
        given:
        byte[] payload = [10, 20, 30] as byte[]
        BlobEntity saved = new BlobEntity(data: payload)
        saved = blobRepository.save(saved).block()

        when: 'single-column @Query exercises ColumnIndexR2dbcResultReader.readBytes'
        byte[] result = blobRepository.findDataById(saved.id).block()

        then:
        result == payload

        cleanup:
        blobRepository.deleteById(saved.id).block()
    }

    void 'test read large BLOB column preserves all bytes'() {
        given:
        byte[] payload = new byte[4096]
        new Random().nextBytes(payload)
        BlobEntity saved = new BlobEntity(data: payload)
        saved = blobRepository.save(saved).block()

        when:
        byte[] result = blobRepository.findDataById(saved.id).block()

        then:
        result == payload
        result.length == 4096

        cleanup:
        blobRepository.deleteById(saved.id).block()
    }

    // -------------------------------------------------------------------------
    // Unit tests for the io.r2dbc.spi.Blob streaming path.
    // These mock a Row that returns an io.r2dbc.spi.Blob so as to directly
    // exercise the Flux.from(blob.stream()).collectList().block() code path
    // in ColumnNameR2dbcResultReader.readBlob and
    // ColumnIndexR2dbcResultReader.readBytes for drivers (e.g. Oracle) that
    // wrap binary data in Blob objects.
    // -------------------------------------------------------------------------

    void 'ColumnNameR2dbcResultReader assembles multi-chunk Blob into single byte array'() {
        given: 'a Row mock that rejects typed access and returns a multi-chunk Blob'
        byte[] chunk1 = [1, 2, 3] as byte[]
        byte[] chunk2 = [4, 5, 6] as byte[]
        byte[] expected = [1, 2, 3, 4, 5, 6] as byte[]

        Blob blob = Mock(Blob) {
            stream() >> Flux.just(ByteBuffer.wrap(chunk1), ByteBuffer.wrap(chunk2))
        }
        Row row = Mock(Row) {
            get("data", byte[].class) >> { throw new IllegalArgumentException("unsupported") }
            get("data") >> blob
        }

        when:
        def reader = new ColumnNameR2dbcResultReader(null)
        byte[] result = reader.readDynamic(row, "data", DataType.BYTE_ARRAY) as byte[]

        then:
        result == expected
    }

    void 'ColumnIndexR2dbcResultReader assembles multi-chunk Blob into single byte array'() {
        given: 'a Row mock that rejects typed access and returns a multi-chunk Blob'
        byte[] chunk1 = [10, 20] as byte[]
        byte[] chunk2 = [30, 40] as byte[]
        byte[] expected = [10, 20, 30, 40] as byte[]

        Blob blob = Mock(Blob) {
            stream() >> Flux.just(ByteBuffer.wrap(chunk1), ByteBuffer.wrap(chunk2))
        }
        Row row = Mock(Row) {
            get(0, byte[].class) >> { throw new IllegalArgumentException("unsupported") }
            get(0) >> blob
        }

        when:
        def reader = new ColumnIndexR2dbcResultReader(null)
        byte[] result = reader.readDynamic(row, 0, DataType.BYTE_ARRAY) as byte[]

        then:
        result == expected
    }

    void 'ColumnNameR2dbcResultReader returns empty array for empty Blob stream'() {
        given:
        Blob blob = Mock(Blob) {
            stream() >> Flux.empty()
        }
        Row row = Mock(Row) {
            get("data", byte[].class) >> { throw new IllegalArgumentException("unsupported") }
            get("data") >> blob
        }

        when:
        def reader = new ColumnNameR2dbcResultReader(null)
        byte[] result = reader.readDynamic(row, "data", DataType.BYTE_ARRAY) as byte[]

        then:
        result != null
        result.length == 0
    }

    void 'ColumnIndexR2dbcResultReader returns empty array for empty Blob stream'() {
        given:
        Blob blob = Mock(Blob) {
            stream() >> Flux.empty()
        }
        Row row = Mock(Row) {
            get(0, byte[].class) >> { throw new IllegalArgumentException("unsupported") }
            get(0) >> blob
        }

        when:
        def reader = new ColumnIndexR2dbcResultReader(null)
        byte[] result = reader.readDynamic(row, 0, DataType.BYTE_ARRAY) as byte[]

        then:
        result != null
        result.length == 0
    }
}
