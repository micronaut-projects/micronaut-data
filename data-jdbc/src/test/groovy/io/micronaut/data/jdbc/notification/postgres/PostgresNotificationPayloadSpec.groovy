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
import io.micronaut.json.JsonMapper
import spock.lang.Specification
import spock.lang.Unroll

class PostgresNotificationPayloadSpec extends Specification {
    private final JsonMapper mapper = JsonMapper.createDefault()

    void "decodes exact string keys without losing numeric precision"() {
        when:
        def payload = PostgresNotificationPayload.decode(mapper, 'Case Sensitive', mapper.writeValueAsString(
                envelope('UPDATE', [Id: '9007199254740993'], [Id: '9007199254740994'])))

        then:
        payload.operation() == ChangeOperation.UPDATE
        payload.metadata().channel() == 'Case Sensitive'
        payload.metadata().oldKeys() == [Id: '9007199254740993']
        payload.metadata().newKeys() == [Id: '9007199254740994']
    }

    @Unroll
    void "invalid envelope is rejected: #changes"() {
        when:
        PostgresNotificationPayload.decode(mapper, 'channel', mapper.writeValueAsString(envelope('INSERT', [:], [id: '1']) + changes))

        then:
        thrown(IllegalArgumentException)

        where:
        changes << [
                [version: 2], [version: '1'], [operation: 'BOGUS'], [schema: ''], [table: null],
                [oldKeys: [id: '1']], [newKeys: [:]], [newKeys: [id: 1]], [newKeys: [id: null]],
                [newKeys: []], [newKeys: ['': '1']], [operation: 'DELETE'],
                [operation: 'UPDATE', oldKeys: [different: '1']], [operation: 'INVALIDATE']
        ]
    }

    @Unroll
    void "decodes operation #operation"() {
        expect:
        PostgresNotificationPayload.decode(mapper, 'channel', mapper.writeValueAsString(envelope(operation, oldKeys, newKeys)))
                .operation().name() == operation

        where:
        operation    | oldKeys   | newKeys
        'INSERT'     | [:]       | [id: '']
        'UPDATE'     | [id: '1'] | [id: '2']
        'DELETE'     | [id: '1'] | [:]
        'INVALIDATE' | [:]       | [:]
    }

    private static Map envelope(String operation, Map oldKeys, Map newKeys) {
        [version: 1, schema: 'public', table: 'book', operation: operation, oldKeys: oldKeys, newKeys: newKeys]
    }
}
