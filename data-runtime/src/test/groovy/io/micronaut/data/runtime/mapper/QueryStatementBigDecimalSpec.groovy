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
package io.micronaut.data.runtime.mapper

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.DataType
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

class QueryStatementBigDecimalSpec extends Specification {

    void "a #value.class.simpleName bound as BIGDECIMAL keeps every digit"() {
        given:
            def statement = new CapturingStatement()
        when:
            statement.setDynamic(null, "p", DataType.BIGDECIMAL, value)
        then:
            statement.values["p"] == expected

        where:
            value                                   | expected
            Long.MAX_VALUE                          | new BigDecimal("9223372036854775807")
            9007199254740993L                       | new BigDecimal("9007199254740993")
            new AtomicLong(9007199254740993L)       | new BigDecimal("9007199254740993")
            new BigInteger("123456789012345678901") | new BigDecimal("123456789012345678901")
            42                                      | new BigDecimal("42")
            1.5d                                    | new BigDecimal("1.5")
    }

    static class CapturingStatement implements QueryStatement<Object, String> {
        final Map<String, Object> values = [:]

        @Override
        QueryStatement<Object, String> setValue(Object statement, String index, Object value) throws DataAccessException {
            values[index] = value
            return this
        }
    }
}
