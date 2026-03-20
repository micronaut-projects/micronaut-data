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

import io.micronaut.data.tck.entities.BasicTypes
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(transactional = false)
class H2ByteArraySpec extends Specification implements H2TestPropertyProvider {

    @Inject
    H2ReactiveBasicTypesRepository repository

    void 'test retrieve byte array via column index reader (single column query)'() {
        given:
        BasicTypes entity = repository.save(new BasicTypes()).block()

        when: 'read single byte[] column by index (exercises ColumnIndexR2dbcResultReader)'
        byte[] result = repository.findByteArrayById(entity.myId).block()

        then:
        result != null
        result == entity.byteArray
        result.class == byte[].class

        cleanup:
        repository.deleteById(entity.myId).block()
    }

    void 'test retrieve byte array via column name reader (full entity query)'() {
        given:
        BasicTypes entity = repository.save(new BasicTypes()).block()

        when: 'read full entity including byte[] field by name (exercises ColumnNameR2dbcResultReader)'
        BasicTypes retrieved = repository.findById(entity.myId).block()

        then:
        retrieved != null
        retrieved.byteArray != null
        retrieved.byteArray == entity.byteArray
        retrieved.byteArray.class == byte[].class

        cleanup:
        repository.deleteById(entity.myId).block()
    }

    void 'test retrieve large byte array via column index reader'() {
        given: 'entity with a larger byte array'
        BasicTypes entity = new BasicTypes()
        entity.byteArray = new byte[1024]
        new Random().nextBytes(entity.byteArray)
        entity = repository.save(entity).block()

        when: 'read single byte[] column for a larger payload (verifies full Blob content is read)'
        byte[] result = repository.findByteArrayById(entity.myId).block()

        then:
        result != null
        result == entity.byteArray
        result.length == 1024

        cleanup:
        repository.deleteById(entity.myId).block()
    }
}
