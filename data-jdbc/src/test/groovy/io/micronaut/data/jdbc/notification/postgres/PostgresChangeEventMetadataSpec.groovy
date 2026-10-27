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
package io.micronaut.data.jdbc.notification.postgres

import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata
import spock.lang.Specification

class PostgresChangeEventMetadataSpec extends Specification {

    void "composite key metadata retains independent immutable old and new values"() {
        given:
        def oldKeys = [tenant: 'a', id: '9007199254740993']
        def newKeys = [tenant: 'b', id: '9007199254740994']
        def metadata = new PostgresChangeEventMetadata('books', 'public', 'Book', oldKeys, newKeys)

        when:
        oldKeys.clear()
        newKeys.id = '1'

        then:
        metadata.oldKeys() == [tenant: 'a', id: '9007199254740993']
        metadata.newKeys() == [tenant: 'b', id: '9007199254740994']

        when:
        metadata.newKeys().put('id', '2')

        then:
        thrown(UnsupportedOperationException)

        when:
        metadata.oldKeys().clear()

        then:
        thrown(UnsupportedOperationException)
    }

    void "delete key remains available through generic event metadata without an entity"() {
        given:
        def metadata = new PostgresChangeEventMetadata('books', 'public', 'book', [id: '42'], [:])
        def event = new DefaultChangeEvent<>(ChangeOperation.DELETE, null, metadata)

        expect:
        event.entity().isEmpty()
        event.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == [id: '42']
        event.metadata(OracleChangeEventMetadata).isEmpty()
        metadata.newKeys().isEmpty()
    }
}
