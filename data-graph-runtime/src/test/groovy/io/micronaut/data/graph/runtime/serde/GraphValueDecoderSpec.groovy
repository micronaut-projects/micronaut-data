package io.micronaut.data.graph.runtime.serde

import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

/**
 * Checks how graph driver values are decoded, independently of a database.
 */
class GraphValueDecoderSpec extends Specification {

    void "values nested deeper than 64 levels are refused"() {
        given:
        def nested = [] as List
        64.times { nested = [nested] }

        when:
        GraphValueDecoder.of(nested).decodeArbitrary()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('deeper than 64')

        when:
        GraphValueDecoder.of(nested).decodeNode()

        then:
        thrown(IllegalArgumentException)

        when: "64 levels are fine"
        def limit = [] as List
        63.times { limit = [limit] }

        then:
        GraphValueDecoder.of(limit).decodeArbitrary() instanceof List
    }

    @Unroll
    void "decode #description"() {
        expect:
        decode.call(GraphValueDecoder.of(value)) == expected

        where:
        description               | value                          | decode                         | expected
        'a string'                | "text"                         | { it.decodeString() }          | "text"
        'bytes as a string'       | [1, 2] as byte[]               | { it.decodeString() }          | "AQI="
        'a number as a string'    | 42                             | { it.decodeString() }          | "42"
        'a boolean'               | true                           | { it.decodeBoolean() }         | true
        'a number as a boolean'   | 0                              | { it.decodeBoolean() }         | false
        'a string as a boolean'   | "true"                         | { it.decodeBoolean() }         | true
        'a byte'                  | 7L                             | { it.decodeByte() }            | (byte) 7
        'a short'                 | 7                              | { it.decodeShort() }           | (short) 7
        'an int from a boolean'   | true                           | { it.decodeInt() }             | 1
        'an int from a string'    | " 12 "                         | { it.decodeInt() }             | 12
        'a long'                  | 12                             | { it.decodeLong() }            | 12L
        'a float'                 | 1.5d                           | { it.decodeFloat() }           | 1.5f
        'a double'                | 2.5f                           | { it.decodeDouble() }          | 2.5d
        'a number'                | 3L                             | { it.decodeNumber() }          | 3L
        'a character'             | 'x' as char                    | { it.decodeChar() }            | 'x' as char
        'a number as a character' | 65                             | { it.decodeChar() }            | 'A' as char
        'a string as a character' | "y"                            | { it.decodeChar() }            | 'y' as char
        'a big integer'           | 5G                             | { it.decodeBigInteger() }      | 5G
        'a big integer from big'  | new BigDecimal("5.7")          | { it.decodeBigInteger() }      | 5G
        'a big integer from long' | 5L                             | { it.decodeBigInteger() }      | 5G
        'a decimal'               | new BigDecimal("1.25")         | { it.decodeBigDecimal() }      | new BigDecimal("1.25")
        'a decimal from integer'  | new BigInteger("3")            | { it.decodeBigDecimal() }      | new BigDecimal("3")
        'a decimal from double'   | 1.5d                           | { it.decodeBigDecimal() }      | new BigDecimal("1.5")
        'a decimal from float'    | 1.5f                           | { it.decodeBigDecimal() }      | new BigDecimal("1.5")
        'a decimal from long'     | 4L                             | { it.decodeBigDecimal() }      | new BigDecimal("4")
        'binary data'             | [1, 2] as byte[]               | { it.decodeBinary() }          | [1, 2] as byte[]
        'binary data from Base64' | "AQI="                         | { it.decodeBinary() }          | [1, 2] as byte[]
        'binary data from a list' | [1, 2, "x"]                    | { it.decodeBinary() }          | [1, 2, 0] as byte[]
        'a native value'          | LocalDate.of(2024, 1, 2)       | { it.decodeNative() }          | LocalDate.of(2024, 1, 2)
        'an arbitrary value'      | [a: [1, [b: 2]], 3: "c"]       | { it.decodeArbitrary() }       | [a: [1, [b: 2]], "3": "c"]
        'null'                    | null                           | { it.decodeNull() }            | true
        'not null'                | "x"                            | { it.decodeNull() }            | false
        'a buffer'                | "x"                            | { it.decodeBuffer().decodeString() } | "x"
    }

    void "decode a tree"() {
        when:
        JsonNode node = GraphValueDecoder.of([
            name: "Ada", born: 1815, height: 1.6d, ratio: 0.5f, big: 10G, decimal: 1.25G, count: 3L, small: (short) 2,
            active: true, missing: null, tags: ["a", "b"], date: LocalDate.of(2024, 1, 2)
        ]).decodeNode()

        then:
        node.get("name").getStringValue() == "Ada"
        node.get("born").getIntValue() == 1815
        node.get("height").getDoubleValue() == 1.6d
        node.get("ratio").getFloatValue() == 0.5f
        node.get("big").getBigIntegerValue() == 10G
        node.get("decimal").getBigDecimalValue() == 1.25G
        node.get("count").getLongValue() == 3L
        node.get("small").getIntValue() == 2
        node.get("active").getBooleanValue()
        node.get("missing").isNull()
        node.get("tags").size() == 2
        node.get("date").getStringValue() == "2024-01-02"
    }

    void "decode arrays and objects"() {
        when:
        def array = GraphValueDecoder.of(["a", "b"]).decodeArray(Argument.of(List))
        def values = []
        while (array.hasNextArrayValue()) {
            values << array.decodeString()
        }
        array.finishStructure()

        then:
        values == ["a", "b"]

        when:
        def primitive = GraphValueDecoder.of([1, 2] as int[]).decodeArray(Argument.of(int[]))
        def ints = []
        while (primitive.hasNextArrayValue()) {
            ints << primitive.decodeInt()
        }

        then:
        ints == [1, 2]

        when:
        def object = GraphValueDecoder.of([name: "Ada", born: 1815]).decodeObject(Argument.of(Map))
        def fields = [:]
        String key
        while ((key = object.decodeKey()) != null) {
            fields[key] = key == "born" ? object.decodeInt() : object.decodeString()
        }
        object.finishStructure()

        then:
        fields == [name: "Ada", born: 1815]
        GraphValueDecoder.of("x").decodeKey() == null

        when: "values are skipped or left"
        def skipped = GraphValueDecoder.of([a: 1, b: 2]).decodeObject(Argument.of(Map))
        skipped.decodeKey()
        skipped.skipValue()
        skipped.finishStructure(true)
        def left = GraphValueDecoder.of(["a", "b"]).decodeArray(Argument.of(List))
        left.hasNextArrayValue()
        left.decodeString()
        left.finishStructure()

        then:
        def e = thrown(SerdeException)
        e.message == "Unexpected element remaining in the structure"
    }

    @Unroll
    void "fail to decode #description"() {
        when:
        decode.call(GraphValueDecoder.of(value))

        then:
        def e = thrown(SerdeException)
        e.message == message

        where:
        description           | value         | decode                                      | message
        'null as a string'    | null          | { it.decodeString() }                       | "Expected a string but found null"
        'text as an int'      | "abc"         | { it.decodeInt() }                          | "Expected an int but found [abc]"
        'a list as a long'    | [1]           | { it.decodeLong() }                         | "Expected a long but found java.util.ArrayList"
        'text as a character' | "ab"          | { it.decodeChar() }                         | "Expected a single character but found [ab]"
        'a map as binary'     | [a: 1]        | { it.decodeBinary() }                       | "Expected binary data but found java.util.LinkedHashMap"
        'a string as array'   | "x"           | { it.decodeArray(Argument.of(List)) }       | "Expected an array but found java.lang.String"
        'a string as object'  | "x"           | { it.decodeObject(Argument.of(Map)) }       | "Expected an object but found java.lang.String"
        'a consumed value'    | "x"           | { it.decodeString(); it.decodeString() }    | "No value to decode"
        'a long as an int'    | 3_000_000_000L | { it.decodeInt() }                         | "Expected an int but found [3000000000], which does not fit in an int"
        'an int as a short'   | 40_000        | { it.decodeShort() }                        | "Expected a short but found [40000], which does not fit in a short"
        'an int as a byte'    | -129          | { it.decodeByte() }                         | "Expected a byte but found [-129], which does not fit in a byte"
        'a double as an int'  | 1e10d         | { it.decodeInt() }                          | "Expected an int but found [1.0E10], which does not fit in an int"
        'text as a byte'      | "300"         | { it.decodeByte() }                         | "Expected a byte but found [300], which does not fit in a byte"
        'a big integer as a long' | 2G ** 64  | { it.decodeLong() }                         | "Expected a long but found [18446744073709551616], which does not fit in a long"
    }

    @Unroll
    void "narrowing #description keeps the value"() {
        expect:
        decode.call(GraphValueDecoder.of(value)) == expected

        where:
        description              | value               | decode              | expected
        'the largest int'        | 2147483647L         | { it.decodeInt() }  | Integer.MAX_VALUE
        'the smallest int'       | -2147483648L        | { it.decodeInt() }  | Integer.MIN_VALUE
        'a fraction to an int'   | 12.75d              | { it.decodeInt() }  | 12
        'a short'                | 32767L              | { it.decodeShort() } | (short) 32767
        'a byte'                 | -128L               | { it.decodeByte() } | (byte) -128
        'a decimal to a long'    | new BigDecimal("9007199254740993") | { it.decodeLong() } | 9007199254740993L
    }

    void "errors name the property being decoded"() {
        given:
        def object = GraphValueDecoder.of([born: "unknown"]).decodeObject(Argument.of(Map))
        object.decodeKey()

        when:
        object.decodeInt()

        then:
        def e = thrown(SerdeException)
        e.message == "Expected an int but found [unknown] (property [born])"
    }
}
