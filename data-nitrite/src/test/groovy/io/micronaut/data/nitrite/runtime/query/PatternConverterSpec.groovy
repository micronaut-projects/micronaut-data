package io.micronaut.data.nitrite.runtime.query

import spock.lang.Specification
import spock.lang.Unroll

class PatternConverterSpec extends Specification {

    @Unroll
    def "looksLikeWildcardPattern: #desc"() {
        expect:
        PatternConverter.looksLikeWildcardPattern(value) == expected

        where:
        desc                  | value    | expected
        "percent wildcard"    | "fo%"    | true
        "underscore wildcard" | "fo_"    | true
        "star wildcard"       | "fo*"    | true
        "regex anchor ^"      | "^foo"   | false
        'regex anchor $'      | 'foo$'   | false
        "regex bracket"       | "[a-z]"  | false
        "regex paren"         | "(foo)"  | false
        "regex pipe"          | "a|b"    | false
        "regex backslash"     | "a\\.b"  | false
        "plain string"        | "foo"    | false
        "null"                | null     | false
        "empty"               | ""       | false
    }

    @Unroll
    def "convertLikeToRegex: #desc"() {
        expect:
        PatternConverter.convertLikeToRegex(pattern) =~ expectedPattern

        where:
        desc                    | pattern   | expectedPattern
        "% becomes .*"          | "foo%"    | /\(\?s\)\^foo\.\*\$/
        "_ becomes ."           | "fo_"     | /\(\?s\)\^fo\.\$/
        "* becomes .*"          | "foo*"    | /\(\?s\)\^foo\.\*\$/
        "no wildcards anchored" | "foo"     | /\(\?s\)\^foo\$/
        "already has ^ prefix"  | "^foo"    | /\(\?s\)\^foo\$/
        "empty pattern"         | ""        | /\(\?s\)\^\$/
        "wrapped (?s) once"     | "foo%bar" | /^\(\?s\)\^foo.*bar\$$/
    }

    @Unroll
    def "resolveRegexPattern: #desc"() {
        expect:
        PatternConverter.resolveRegexPattern(input) == expected

        where:
        desc                      | input             | expected
        "null returns empty"       | null              | ""
        "Pattern object"           | ~/J.*/            | "J.*"
        "slash notation stripped"  | "/foo.*/"         | "foo.*"
        "wildcard string converted"| "foo%"            | PatternConverter.convertLikeToRegex("foo%")
        "plain regex passthrough"  | "^foo\$"          | "^foo\$"
        "plain string passthrough" | "foo"             | "foo"
    }
}
