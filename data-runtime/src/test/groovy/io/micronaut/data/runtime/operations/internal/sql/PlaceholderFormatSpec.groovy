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
package io.micronaut.data.runtime.operations.internal.sql

import spock.lang.Specification
import spock.lang.Unroll

class PlaceholderFormatSpec extends Specification {

    @Unroll
    void "placeholder format '#format' produces the same text as String.format"() {
        given:
            def placeholderFormat = DefaultSqlPreparedQuery.PlaceholderFormat.of(format)
        expect:
            [1, 9, 10, 99, 100, 1000, 12345].every { int index ->
                def builder = new StringBuilder("x")
                placeholderFormat.append(builder, index)
                builder.toString() == "x" + String.format(format, index)
            }
            placeholderFormat.formatted() == formatted
        where:
            format    | formatted
            "?"       | false
            "\$%s"    | false
            "@p%s"    | false
            ":p%s"    | false
            "%s"      | false
            "?%s?"    | false
            "plain"   | false
            "%d"      | true
            "p%1\$s"  | true
            "%%s"     | true
            "%s%%"    | true
    }

    void "the estimated length covers the placeholder and a separator"() {
        expect:
            DefaultSqlPreparedQuery.PlaceholderFormat.of("?").maxLength(1000) == 2
            DefaultSqlPreparedQuery.PlaceholderFormat.of("\$%s").maxLength(1000) == 6
            DefaultSqlPreparedQuery.PlaceholderFormat.of("@p%s").maxLength(9) == 4
    }
}
