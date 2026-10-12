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

import java.sql.Array
import java.sql.Time
import java.time.Instant
import java.time.ZoneOffset

import static io.micronaut.data.model.DataType.*

class QueryStatementSpec extends Specification {

    void "scalar #type dispatches its existing setter without conversion"() {
        given:
        def writer = new ConvertingStatement()

        when:
        def result = writer.setDynamic("statement", 7, type, input)

        then:
        result.is(writer.result)
        writer.call == [setter, "statement", 7, expected]
        writer.conversions.empty

        where:
        type       | input                                               | setter         | expected
        STRING     | "text"                                              | "string"       | "text"
        STRING     | new StringBuilder("text")                           | "string"       | "text"
        STRING     | Day.MONDAY                                          | "string"       | "monday"
        JSON       | new StringBuilder("{}")                             | "string"       | "{}"
        INTEGER    | 7L                                                  | "int"          | 7
        BOOLEAN    | true                                                | "boolean"      | true
        DATE       | new Date(1234)                                      | "date"         | new Date(1234)
        TIMESTAMP  | Instant.ofEpochSecond(1234)                          | "timestamp"    | Instant.ofEpochSecond(1234)
        TIMESTAMP  | Instant.ofEpochSecond(1234).atZone(ZoneOffset.UTC)   | "timestamp"    | Instant.ofEpochSecond(1234)
        TIME       | Time.valueOf("12:34:56")                             | "time"         | Time.valueOf("12:34:56")
        DataType.UUID | uuid()                                           | "value"        | uuid()
        DataType.UUID | new StringBuilder(uuid().toString())              | "value"        | uuid()
        DOUBLE     | 7L                                                  | "double"       | 7d
        BYTE_ARRAY | [1, 2] as byte[]                                    | "bytes"        | [1, 2] as byte[]
        BIGDECIMAL | new BigDecimal("1.234567890123456789")              | "bigDecimal"   | new BigDecimal("1.234567890123456789")
        BIGDECIMAL | 7L                                                  | "bigDecimal"   | BigDecimal.valueOf(7d)
        LONG       | 7                                                   | "long"         | 7L
        CHARACTER  | 'x' as char                                         | "char"         | 'x' as char
        FLOAT      | 7L                                                  | "float"        | 7f
        SHORT      | 7L                                                  | "short"        | 7 as short
        BYTE       | 7L                                                  | "byte"         | 7 as byte
    }

    void "scalar #type keeps the overridable conversion target #target"() {
        given:
        def value = new Object()
        def writer = new ConvertingStatement(converted: converted)

        when:
        def result = writer.setDynamic("statement", 7, type, value)

        then:
        result.is(writer.result)
        writer.call == [setter, "statement", 7, converted]
        writer.conversions == [[value, target]]

        where:
        type       | target         | setter       | converted
        STRING     | String         | "string"     | "text"
        JSON       | String         | "string"     | "{}"
        INTEGER    | Integer        | "int"        | 7
        BOOLEAN    | Boolean        | "boolean"    | true
        DATE       | java.sql.Date  | "date"       | java.sql.Date.valueOf("2020-01-02")
        TIMESTAMP  | Instant        | "timestamp"  | Instant.ofEpochSecond(1234)
        DOUBLE     | Double         | "double"     | 7d
        BYTE_ARRAY | byte[]         | "bytes"      | [1, 2] as byte[]
        BIGDECIMAL | BigDecimal     | "bigDecimal" | new BigDecimal("7.25")
        LONG       | Long           | "long"       | 7L
        CHARACTER  | Character      | "char"       | 'x' as char
        FLOAT      | Float          | "float"      | 7f
        SHORT      | Short          | "short"      | 7 as short
        BYTE       | Byte           | "byte"       | 7 as byte
    }

    void "primitive scalar #type still rejects null before dispatch or conversion"() {
        given:
        def writer = new ConvertingStatement()

        when:
        writer.setDynamic("statement", 7, type, null)

        then:
        def error = thrown(DataAccessException)
        error.message == "Cannot set null value"
        writer.call == null
        writer.conversions.empty

        where:
        type << [INTEGER, BOOLEAN, DOUBLE, LONG, CHARACTER, FLOAT, SHORT, BYTE]
    }

    void "nullable scalar #type retains specialized null dispatch"() {
        given:
        def writer = new ConvertingStatement()

        expect:
        writer.setDynamic("statement", 7, type, null).is(writer.result)
        writer.call == [setter, "statement", 7, null]
        writer.conversions.empty

        where:
        type       | setter
        STRING     | "string"
        JSON       | "string"
        DATE       | "date"
        TIMESTAMP  | "timestamp"
        TIME       | "time"
        BYTE_ARRAY | "bytes"
        BIGDECIMAL | "bigDecimal"
    }

    void "array #type retains its conversion target #target"() {
        given:
        def writer = new ConvertingStatement(converted: converted)

        expect:
        writer.setDynamic("statement", 7, type, input).is(writer.result)
        writer.call == ["array", "statement", 7, converted]
        writer.conversions == [[input, target]]

        where:
        type            | input                      | target     | converted
        SHORT_ARRAY     | [1, 2] as short[]          | Short[]    | [1, 2] as Short[]
        LONG_ARRAY      | [1, 2] as long[]           | Long[]     | [1, 2] as Long[]
        FLOAT_ARRAY     | [1, 2] as float[]          | Float[]    | [1, 2] as Float[]
        INTEGER_ARRAY   | [1, 2] as int[]            | Integer[]  | [1, 2] as Integer[]
        DOUBLE_ARRAY    | [1, 2] as double[]         | Double[]   | [1, 2] as Double[]
        BOOLEAN_ARRAY   | [true, false] as boolean[] | Boolean[]  | [true, false] as Boolean[]
        STRING_ARRAY    | ["one", "two"]             | String[]   | ["one", "two"] as String[]
        CHARACTER_ARRAY | ['a', 'b'] as char[]       | String[]   | ["a", "b"] as String[]
        UUID_ARRAY      | [uuid()]                   | UUID[]     | [uuid()] as UUID[]
        CHARACTER_ARRAY | ['a', 'b'] as Character[]  | String[]   | ["a", "b"] as String[]
        INTEGER_ARRAY   | ['a', 'b'] as Character[]  | String[]   | ["a", "b"] as String[]
    }

    void "array #type preserves null SQL arrays and reference arrays without conversion"() {
        given:
        def writer = new ConvertingStatement()
        def sqlArray = Stub(Array)
        def referenceArray = [new Object()] as Object[]

        expect:
        [null, sqlArray, referenceArray].every { value ->
            writer.setDynamic("statement", 7, type, value).is(writer.result) &&
                writer.call == ["array", "statement", 7, value]
        }
        writer.conversions.empty

        where:
        type << DataType.values().findAll { it.isArray() && it != BYTE_ARRAY }
    }

    void "default data type #type retains value dispatch"() {
        given:
        def writer = new ConvertingStatement()
        def value = new Object()

        expect:
        [value, null].every { input ->
            writer.setDynamic("statement", 7, type, input).is(writer.result) &&
                writer.call == ["value", "statement", 7, input]
        }
        writer.conversions.empty

        where:
        type << [OBJECT, ENTITY, DURATION, PERIOD]
    }

    void "invalid #type value retains #errorType and its message"() {
        given:
        def writer = new RecordingStatement()

        when:
        writer.setDynamic("statement", 7, type, value)

        then:
        def error = thrown(errorType)
        error.message == message
        writer.call == null

        where:
        type | value         | errorType                | message
        TIME | "12:34:56"    | DataAccessException      | "Invalid time: 12:34:56"
        DataType.UUID | null       | DataAccessException      | "Invalid UUID: null"
        DataType.UUID | 7          | DataAccessException      | "Invalid UUID: 7"
        DataType.UUID | "bad-uuid" | IllegalArgumentException | "Invalid UUID string: bad-uuid"
    }

    void "failed conversion for #type preserves the default error"() {
        given:
        def writer = new RecordingStatement()
        def value = new Object()

        when:
        writer.setDynamic("statement", 7, type, value)

        then:
        def error = thrown(DataAccessException)
        error.message == "Cannot convert type [${value.class}] to target type: ${target}. Consider defining a TypeConverter bean to handle this case."
        writer.call == null

        where:
        type          | target
        INTEGER       | Integer
        BOOLEAN       | Boolean
        DATE          | java.sql.Date
        TIMESTAMP     | Instant
        DOUBLE        | Double
        BYTE_ARRAY    | byte[]
        BIGDECIMAL    | BigDecimal
        LONG          | Long
        CHARACTER     | Character
        FLOAT         | Float
        SHORT         | Short
        BYTE          | Byte
        INTEGER_ARRAY | Integer[]
    }

    void "numeric #type keeps a null result from an overridden converter boxed"() {
        given:
        def writer = new ConvertingStatement(converted: null)

        expect:
        writer.setDynamic("statement", 7, type, "custom").is(writer.result)
        writer.call == [setter, "statement", 7, null]
        writer.conversions.size() == 1

        where:
        type    | setter
        INTEGER | "int"
        DOUBLE  | "double"
        LONG    | "long"
        FLOAT   | "float"
        SHORT   | "short"
        BYTE    | "byte"
    }

    void "default setters retain their return value behavior"() {
        given:
        def writer = new ValueOnlyStatement()

        expect:
        writer.setDynamic("statement", 7, INTEGER, 7).is(writer.result)
        writer.call == ["value", "statement", 7, 7]
        writer.setDynamic("statement", 7, LONG, 7).is(writer)
        writer.call == ["value", "statement", 7, 7L]
    }

    private static UUID uuid() {
        UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    }

    private enum Day {
        MONDAY

        @Override
        String toString() { "monday" }
    }

    private static class ValueOnlyStatement implements QueryStatement<Object, Integer> {
        List call
        QueryStatement<Object, Integer> result = this

        ValueOnlyStatement() {
            result = new QueryStatement<Object, Integer>() {
                @Override
                QueryStatement<Object, Integer> setValue(Object statement, Integer index, Object value) { this }
            }
        }

        protected QueryStatement<Object, Integer> record(String setter, Object statement, Integer index, Object value) {
            call = [setter, statement, index, value]
            result
        }

        @Override
        QueryStatement<Object, Integer> setValue(Object statement, Integer index, Object value) { record("value", statement, index, value) }
    }

    private static class RecordingStatement extends ValueOnlyStatement {
        @Override QueryStatement<Object, Integer> setString(Object s, Integer i, String v) { record("string", s, i, v) }
        @Override QueryStatement<Object, Integer> setInt(Object s, Integer i, Integer v) { record("int", s, i, v) }
        @Override QueryStatement<Object, Integer> setBoolean(Object s, Integer i, Boolean v) { record("boolean", s, i, v) }
        @Override QueryStatement<Object, Integer> setDate(Object s, Integer i, Date v) { record("date", s, i, v) }
        @Override QueryStatement<Object, Integer> setTimestamp(Object s, Integer i, Instant v) { record("timestamp", s, i, v) }
        @Override QueryStatement<Object, Integer> setTime(Object s, Integer i, Time v) { record("time", s, i, v) }
        @Override QueryStatement<Object, Integer> setDouble(Object s, Integer i, Double v) { record("double", s, i, v) }
        @Override QueryStatement<Object, Integer> setBytes(Object s, Integer i, byte[] v) { record("bytes", s, i, v) }
        @Override QueryStatement<Object, Integer> setBigDecimal(Object s, Integer i, BigDecimal v) { record("bigDecimal", s, i, v) }
        @Override QueryStatement<Object, Integer> setLong(Object s, Integer i, Long v) { record("long", s, i, v) }
        @Override QueryStatement<Object, Integer> setChar(Object s, Integer i, Character v) { record("char", s, i, v) }
        @Override QueryStatement<Object, Integer> setFloat(Object s, Integer i, Float v) { record("float", s, i, v) }
        @Override QueryStatement<Object, Integer> setShort(Object s, Integer i, Short v) { record("short", s, i, v) }
        @Override QueryStatement<Object, Integer> setByte(Object s, Integer i, Byte v) { record("byte", s, i, v) }
        @Override QueryStatement<Object, Integer> setArray(Object s, Integer i, Object v) { record("array", s, i, v) }
    }

    private static class ConvertingStatement extends RecordingStatement {
        List conversions = []
        Object converted

        @Override
        public <T> T convertRequired(Object value, Class<T> type) {
            conversions << [value, type]
            (T) converted
        }
    }
}
