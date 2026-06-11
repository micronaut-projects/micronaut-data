package io.micronaut.data.nitrite.runtime

import spock.lang.Specification

class NameUtilsSpec extends Specification {

    void "test camelToSnake"() {
        expect:
        NameUtils.camelToSnake(input) == output

        where:
        input           | output
        null            | null
        ""              | ""
        "foo"           | "foo"
        "fooBar"        | "foo_bar"
        "FooBar"        | "foo_bar"
        "aB"            | "a_b"
    }

    void "test snakeToCamel"() {
        expect:
        NameUtils.snakeToCamel(input) == output

        where:
        input           | output
        null            | null
        ""              | ""
        "foo"           | "foo"
        "foo_bar"       | "fooBar"
        "foo__bar"      | "fooBar"
        "_foo"          | "Foo"
    }

    void "test snakeToCamelPath"() {
        expect:
        NameUtils.snakeToCamelPath(input) == output

        where:
        input                   | output
        null                    | null
        "foo_bar"               | "fooBar"
        "foo_bar.baz_qux"       | "fooBar.bazQux"
        "simple"                | "simple"
        "simple.path"           | "simple.path"
    }
}
