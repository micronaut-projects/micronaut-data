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
package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.r2dbc.h2.BlobEntity
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

/**
 * Integration tests for Blob reading with Oracle R2DBC.
 *
 * Oracle R2DBC returns BLOB columns as {@code io.r2dbc.spi.Blob} objects that
 * emit data as a stream of {@code ByteBuffer} chunks. These tests therefore
 * directly exercise the {@code Flux.from(blob.stream()).collectList().block()}
 * path in both {@code ColumnNameR2dbcResultReader} (full entity fetch) and
 * {@code ColumnIndexR2dbcResultReader} (single-column {@code @Query}).
 */
@MicronautTest(transactional = false)
class OracleXEBlobSpec extends Specification implements OracleXETestPropertyProvider {

    @Inject
    OracleBlobEntityRepository blobRepository

    void 'test read BLOB column via column name reader (full entity fetch)'() {
        given:
        byte[] payload = [1, 2, 3, 4, 5] as byte[]
        BlobEntity saved = new BlobEntity(data: payload)
        saved = blobRepository.save(saved).block()

        when: 'full entity fetch exercises ColumnNameR2dbcResultReader.readBlob via Oracle Blob stream'
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

        when: 'single-column @Query exercises ColumnIndexR2dbcResultReader.readBytes via Oracle Blob stream'
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

        when: 'large payload verifies Flux.collectList() assembles all Blob chunks without truncation'
        byte[] result = blobRepository.findDataById(saved.id).block()

        then:
        result == payload
        result.length == 4096

        cleanup:
        blobRepository.deleteById(saved.id).block()
    }
}
