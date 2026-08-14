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
package io.micronaut.data.jdbc.notification

import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata
import spock.lang.Specification

class DefaultChangeEventSpec extends Specification {

    void "an invalidation-only provider does not need row-level data"() {
        when:
        ChangeEvent<Book> event = new InvalidationOnlyProvider().notification()

        then:
        event.operation() == ChangeOperation.INVALIDATE
        event.entity().isEmpty()
        event.metadata(OracleChangeEventMetadata).isEmpty()
    }

    void "provider metadata is exposed only through its requested type"() {
        given:
        def metadata = new OracleChangeEventMetadata('AAABBB')
        ChangeEvent<Book> event = new DefaultChangeEvent<>(ChangeOperation.DELETE, null, metadata)

        expect:
        event.entity().isEmpty()
        event.metadata(OracleChangeEventMetadata).orElseThrow().is(metadata)
        event.metadata(UnrelatedMetadata).isEmpty()
    }

    private static final class InvalidationOnlyProvider {
        ChangeEvent<Book> notification() {
            return new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null)
        }
    }

    private static final class Book {
    }

    private static final class UnrelatedMetadata implements ChangeEventMetadata {
    }
}
