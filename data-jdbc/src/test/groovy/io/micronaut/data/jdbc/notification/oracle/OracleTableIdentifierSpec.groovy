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
package io.micronaut.data.jdbc.notification.oracle

import spock.lang.Specification

class OracleTableIdentifierSpec extends Specification {

    void "matches Oracle notification table name #changedTableName against #mappedTableName"() {
        expect:
        OracleTableIdentifier.parse(mappedTableName).matches(changedTableName) == matches

        where:
        mappedTableName | changedTableName     || matches
        "book"          | "BOOK"               || true
        "BOOK"          | "APP.BOOK"           || true
        '"BOOK"'        | '"APP"."BOOK"'       || true
        '"Book"'        | '"APP"."Book"'       || true
        '"Book"'        | '"APP"."BOOK"'       || false
        '"APP"."BOOK"'  | '"APP"."BOOK"'       || true
        '"APP"."BOOK"'  | '"OTHER_APP"."BOOK"' || false
        '"APP"."A.B"'   | '"APP"."A.B"'        || true
        '"APP"."A.B"'   | '"APP.A"."B"'        || false
        '"APP"."A""B"'  | '"APP"."A""B"'       || true
        "BOOK"          | "APP.BOOK_HISTORY"   || false
    }

    void "preserves the Oracle SQL identifier"() {
        expect:
        OracleTableIdentifier.parse(input).sqlName() == output

        where:
        input          || output
        "book"         || "book"
        "app.book"     || "app.book"
        '"App"."Book"' || '"App"."Book"'
        '"App"."A.B"'  || '"App"."A.B"'
        '"App"."A""B"' || '"App"."A""B"'
    }

    void "rejects invalid Oracle table identifier #identifier"() {
        when:
        OracleTableIdentifier.parse(identifier)

        then:
        thrown(IllegalArgumentException)

        where:
        identifier << ["", "BOOK.", ".BOOK", "APP..BOOK", '"APP"."BOOK', "APP.BOOK.EXTRA"]
    }
}
