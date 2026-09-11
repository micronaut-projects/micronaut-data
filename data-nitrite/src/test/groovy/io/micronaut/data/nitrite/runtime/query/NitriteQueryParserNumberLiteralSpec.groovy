package io.micronaut.data.nitrite.runtime.query

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Focused coverage for the numeric-literal parsing path exercised via
 * {@link NitriteQueryParser#parseJson(String)} — the int/long/BigInteger
 * narrowing and double/BigDecimal precision fallback, plus the surrounding
 * parameter-reference and non-numeric-string fallback behavior.
 */
class NitriteQueryParserNumberLiteralSpec extends Specification {

    def parser = new NitriteQueryParser()

    private Object literal(String raw) {
        (parser.parseJson('{"v":' + raw + '}') as Map).v
    }

    @Unroll
    def "integral literal #raw narrows to #expectedType with value #expected"() {
        when:
        def value = literal(raw)

        then:
        value == expected
        value.class == expectedType

        where:
        raw                     | expected                                  | expectedType
        "0"                     | 0                                         | Integer
        "-0"                    | 0                                         | Integer
        "007"                   | 7                                         | Integer
        "42"                    | 42                                        | Integer
        "-42"                   | -42                                       | Integer
        "2147483647"            | Integer.MAX_VALUE                         | Integer
        "-2147483648"           | Integer.MIN_VALUE                         | Integer
        "2147483648"            | 2147483648L                               | Long
        "-2147483649"           | -2147483649L                              | Long
        "9223372036854775807"   | Long.MAX_VALUE                            | Long
        "-9223372036854775808"  | Long.MIN_VALUE                            | Long
        "9223372036854775808"   | new BigInteger("9223372036854775808")     | BigInteger
        "-9223372036854775809"  | new BigInteger("-9223372036854775809")    | BigInteger
        "99999999999999999999999999999" | new BigInteger("99999999999999999999999999999") | BigInteger
    }

    @Unroll
    def "decimal literal #raw parses to #expectedType with value #expected"() {
        when:
        def value = literal(raw)

        then:
        value == expected
        value.class == expectedType

        where:
        raw                            | expected                                          | expectedType
        "0.0"                          | 0.0d                                              | Double
        "1.5"                          | 1.5d                                              | Double
        "-1.5"                         | -1.5d                                             | Double
        "100.25"                       | 100.25d                                           | Double
        "1e10"                         | 1.0e10d                                           | Double
        "1E10"                         | 1.0e10d                                           | Double
        "1.5e2"                        | 150.0d                                            | Double
        "0.12345678901234567890"       | new BigDecimal("0.12345678901234567890")          | BigDecimal
        "3.14159265358979323846"       | new BigDecimal("3.14159265358979323846")          | BigDecimal
    }

    @Unroll
    def "non-numeric token #raw falls back to the literal string"() {
        expect:
        literal(raw) == expected

        where:
        raw        | expected
        "1.2.3"    | "1.2.3"
        "12xyz"    | "12xyz"
        "42abc"    | "42abc"
        "abc"      | "abc"
    }

    @Unroll
    def "parameter reference #raw is preserved as-is"() {
        expect:
        literal(raw) == expected

        where:
        raw                  | expected
        ':age'                | ':age'
        ':minAge'             | ':minAge'
        '$mn_qp:0'            | '$mn_qp:0'
        '$mn_qp:1'            | '$mn_qp:1'
    }
}
