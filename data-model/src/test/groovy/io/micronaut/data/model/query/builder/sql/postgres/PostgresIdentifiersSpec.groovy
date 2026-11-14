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
package io.micronaut.data.model.query.builder.sql.postgres

import spock.lang.Specification

class PostgresIdentifiersSpec extends Specification {

    void "normalizes identifier case: #identifier, quoted=#quoted"() {
        expect:
        PostgresIdentifiers.normalize(identifier, quoted) == expected

        where:
        identifier | quoted | expected
        'Book_ID'  | false  | 'book_id'
        'Book_ID'  | true   | 'Book_ID'
        'ÄBook'    | false  | 'Äbook'
        'ÄBook'    | true   | 'ÄBook'
        'İΣBOOK'   | false  | 'İΣbook'
        'İΣBOOK'   | true   | 'İΣBOOK'
        'a_b9$'    | false  | 'a_b9$'
        'A"B'      | true   | 'A"B'
    }

    void "preserves complete UTF-8 characters at the identifier limit: #description"() {
        expect:
        PostgresIdentifiers.normalize(identifier, false) == expected
        PostgresIdentifiers.normalize(identifier, true) == expected

        where:
        description                  | identifier      | expected
        'ASCII below limit'          | 'a' * 62        | 'a' * 62
        'ASCII at limit'             | 'a' * 63        | 'a' * 63
        'ASCII above limit'          | 'a' * 64        | 'a' * 63
        'two-byte character fits'    | 'a' * 61 + 'é'  | 'a' * 61 + 'é'
        'two-byte character cut'     | 'a' * 62 + 'é'  | 'a' * 62
        'three-byte character fits'  | 'a' * 60 + '界' | 'a' * 60 + '界'
        'three-byte character cut 1' | 'a' * 61 + '界' | 'a' * 61
        'three-byte character cut 2' | 'a' * 62 + '界' | 'a' * 62
        'four-byte character fits'   | 'a' * 59 + '😀'  | 'a' * 59 + '😀'
        'four-byte character cut 1'  | 'a' * 60 + '😀'  | 'a' * 60
        'four-byte character cut 2'  | 'a' * 61 + '😀'  | 'a' * 61
        'four-byte character cut 3'  | 'a' * 62 + '😀'  | 'a' * 62
        'cut between characters'     | 'é' * 32        | 'é' * 31
        'character after limit'      | 'a' * 63 + 'é'  | 'a' * 63
    }

    void "folds ASCII case before truncating unquoted identifiers"() {
        expect:
        PostgresIdentifiers.normalize('A' * 64, false) == 'a' * 63
        PostgresIdentifiers.normalize('A' * 64, true) == 'A' * 63
    }
}
