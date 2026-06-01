package io.micronaut.data.nitrite.runtime.query

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class ValueResolverSpec extends Specification {

    // null entityMapper is safe for all methods except resolveCollection
    ValueResolver resolver = new ValueResolver(null)

    @Unroll
    def "resolveValueInternal positional placeholder: #desc"() {
        expect:
        resolver.resolveValueInternal(value, params as Object[], [:]) == expected

        where:
        desc                  | value       | params         | expected
        "\$mn_qp:0"           | '$mn_qp:0'  | ['hello']      | 'hello'
        "\$mn_qp:1"           | '$mn_qp:1'  | ['a', 'b']     | 'b'
        // out-of-range / null params fall through to interpolation path and produce ""
        "index out of range"  | '$mn_qp:5'  | ['x']          | ''
        "null params"         | '$mn_qp:0'  | null           | ''
    }

    @Unroll
    def "resolveValueInternal named placeholder: #desc"() {
        expect:
        resolver.resolveValueInternal(value, [] as Object[], named) == expected

        where:
        desc              | value    | named           | expected
        ":name found"     | ':name'  | [name: 'Alice'] | 'Alice'
        ":name not found" | ':name'  | [:]             | ':name'
    }

    @Unroll
    def "resolveValueInternal map placeholder: #desc"() {
        expect:
        resolver.resolveValueInternal(value, params as Object[], [:]) == expected

        where:
        desc                  | value              | params     | expected
        "Map \$mn_qp:0"       | ['$mn_qp': 0]      | ['hello']  | 'hello'
        "Map \$mn_qp:1"       | ['$mn_qp': 1]      | ['a', 'b'] | 'b'
        "non-placeholder map" | [foo: 'bar']       | []         | [foo: 'bar']
        "wrong key map"       | [other: 0]         | ['x']      | [other: 0]
    }

    @Unroll
    def "resolveValueInternal passthrough: #desc"() {
        expect:
        resolver.resolveValueInternal(value, [] as Object[], [:]) == expected

        where:
        desc      | value  | expected
        "null"    | null   | null
        "Integer" | 42     | 42
        "String"  | "foo"  | "foo"
        "Boolean" | true   | true
    }

    @Unroll
    def "preConvertForFilter: #desc"() {
        expect:
        resolver.preConvertForFilter(value) == expected

        where:
        desc        | value                                     | expected
        "null"      | null                                      | null
        "String"    | "foo"                                     | "foo"
        "Integer"   | 42                                        | 42
        "LocalDate" | LocalDate.of(2024, 1, 15)                | LocalDate.of(2024, 1, 15).toEpochDay()
        "LocalTime" | LocalTime.of(10, 30)                     | LocalTime.of(10, 30).toNanoOfDay()
    }

    def "preConvertForFilter LocalDateTime and Instant convert to epoch nanos"() {
        given:
        def ldt = LocalDateTime.of(2024, 1, 15, 10, 30)
        def instant = ldt.toInstant(ZoneOffset.UTC)
        def expectedNanos = instant.epochSecond * 1_000_000_000L + instant.nano

        expect:
        resolver.preConvertForFilter(ldt)     == expectedNanos
        resolver.preConvertForFilter(instant) == expectedNanos
    }

    @Unroll
    def "maybeCoerceUuid: #desc"() {
        given:
        def uuid = '550e8400-e29b-41d4-a716-446655440000'

        expect:
        resolver.maybeCoerceUuid(field, value) == expected

        where:
        desc                 | field  | value                                  | expected
        "id field coerced"   | 'id'   | '550e8400-e29b-41d4-a716-446655440000' | UUID.fromString('550e8400-e29b-41d4-a716-446655440000')
        "_id field coerced"  | '_id'  | '550e8400-e29b-41d4-a716-446655440000' | UUID.fromString('550e8400-e29b-41d4-a716-446655440000')
        "other field"        | 'name' | '550e8400-e29b-41d4-a716-446655440000' | '550e8400-e29b-41d4-a716-446655440000'
        "invalid UUID"       | 'id'   | 'not-a-uuid'                           | 'not-a-uuid'
        "non-string value"   | 'id'   | 42                                     | 42
    }

    @Unroll
    def "isPlaceholder via compileValue: #desc"() {
        // compileValue exercises the placeholder detection path
        expect:
        resolver.compileValue(value).resolve(params as Object[], [:]) == expected

        where:
        desc                   | value         | params    | expected
        "positional string"    | '$mn_qp:0'    | ['hello'] | 'hello'
        "named string"         | ':name'       | []        | null      // NamedParameter.resolve returns null when key absent
        "literal string"       | 'foo'         | []        | 'foo'
        "literal integer"      | 42            | []        | 42
        "Map placeholder"      | ['$mn_qp': 0] | ['world'] | 'world'
    }
}
