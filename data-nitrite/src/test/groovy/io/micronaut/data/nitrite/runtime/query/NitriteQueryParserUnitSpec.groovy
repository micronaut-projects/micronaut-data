package io.micronaut.data.nitrite.runtime.query

import spock.lang.Specification
import spock.lang.Unroll

class NitriteQueryParserUnitSpec extends Specification {

    def parser = new NitriteQueryParser()

    @Unroll
    def "parseJson parses simple JSON values: #json -> #expected"() {
        expect:
        parser.parseJson(json) == expected

        where:
        json                | expected
        "{}"                | [:]
        '{"a":1}'           | [a: 1]
        '{"a":1,"b":2}'     | [a: 1, b: 2]
        '{"a":true}'        | [a: true]
        '{"a":false}'       | [a: false]
        '{"a":null}'        | [a: null]
        '{"a":1.5}'         | [a: 1.5d]
        '{"a":"text"}'      | [a: "text"]
        '{"a":[]}'          | [a: []]
        '{"a":{}}'          | [a: [:]]
    }

    @Unroll
    def "parseJson handles escaped quotes correctly: #json -> #expected"() {
        expect:
        parser.parseJson(json) == expected

        where:
        json                                            | expected
        '{"name":"O\\\'Reilly"}'                       | [name: "O'Reilly"]
        '{"text":"He said \\\"Hello\\\""}'              | [text: 'He said "Hello"']
        '{"path":"C:\\\\Users\\\\me"}'                  | [path: "C:\\Users\\me"]
        '{"str":"\\n\\r\\t"}'                          | [str: "\n\r\t"]
    }

    @Unroll
    def "parseJson preserves placeholders: #json -> #expected"() {
        expect:
        parser.parseJson(json) == expected

        where:
        json                                                       | expected
        '{"age":":age"}'                                           | [age: ":age"]
        '{"age":"' + '$mn_qp:0"}'                                  | [age: '$mn_qp:0']
        '{"age":":minAge","name":":name"}'                         | [age: ":minAge", name: ":name"]
        '{"arr":[":x", "' + '$mn_qp:1"]}'                          | [arr: [":x", '$mn_qp:1']]
    }

    def "parseJson handles nested objects and arrays"() {
        given:
        def json = '{"person":{"name":"John","age":30},"tags":["a","b"],"address":{"city":"NYC","zip":10001}}'
        def expected = [
                person: [name: "John", age: 30],
                tags  : ["a", "b"],
                address: [city: "NYC", zip: 10001]
        ]

        expect:
        parser.parseJson(json) == expected
    }

    def "parseJson handles JSON array"() {
        given:
        def json = '[{"a":1},{"b":2},"x",{"c":[]}]'
        def expected = [[a: 1], [b: 2], "x", [c: []]]

        expect:
        parser.parseJson(json) == expected
    }

    def "parseJson handles empty array"() {
        expect:
        parser.parseJson("[]") == []
        parser.parseJson("[  ]") == []
    }

    def "parseJson throws for invalid input"() {
        when:
        parser.parseJson("not json")

        then:
        thrown(IllegalArgumentException)
    }

    def "parseJson throws for bare text"() {
        when:
        parser.parseJson("hello world")

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "parseJsonObject: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                                        | json                          | expected
        "single-quoted key int"                     | "{'a':1}"                     | [a: 1]
        "single-quoted key string"                  | "{'name':'John'}"             | [name: "John"]
        "unquoted key int"                          | "{a:1}"                       | [a: 1]
        "unquoted key single-quoted value"          | "{name:'John'}"               | [name: "John"]
        "single-quoted string value x"              | '{"a":\'x\'}'                 | [a: "x"]
        "single-quoted string value hello"          | '{"a":\'hello\'}'             | [a: "hello"]
        "bare named parameter :value"               | "{key::value}"                | [key: ":value"]
        "bare named parameter :minAge"              | "{key::minAge}"               | [key: ":minAge"]
        "bare placeholder \$mn_qp:0"               | "{key:\$mn_qp:0}"             | [key: '$mn_qp:0']
        "bare placeholder \$mn_qp:1"               | "{key:\$mn_qp:1}"             | [key: '$mn_qp:1']
        "trailing whitespace after value"           | '{"a": "b"  }'                | [a: "b"]
        "leading whitespace before object"          | '  {"a":1}  '                 | [a: 1]
        "empty braces"                              | "{}"                          | [:]
        "token true"                                | '{"a":true}'                  | [a: true]
        "token false"                               | '{"a":false}'                 | [a: false]
        "quoted value 1.2.3 stays string"           | '{"a":"1.2.3"}'               | [a: "1.2.3"]
        "quoted value 12xyz stays string"           | '{"a":"12xyz"}'               | [a: "12xyz"]
    }

    @Unroll
    def "parseJsonArray: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                          | json                                    | expected
        "single-quoted ['a']"         | "['a']"                                 | ["a"]
        "single-quoted ['x','y']"     | "['x','y']"                             | ["x", "y"]
        "nested arrays"               | '[[1,2],[3,4]]'                         | [[1, 2], [3, 4]]
        "bare ints"                   | "[1,2,3]"                               | [1, 2, 3]
        "bare doubles"                | "[1.5,2.5]"                             | [1.5d, 2.5d]
        "dq escaped quote"            | '["he\\"llo"]'                          | ['he"llo']
        "dq escaped newline"          | '["a\\nb"]'                             | ["a\nb"]
        "mixed types"                 | '[1,"a",true,false,null,{},[]]'         | [1, "a", true, false, null, [:], []]
    }

    @Unroll
    def "parseJsonObject single-quoted value escapes: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                              | json                          | expected
        "escaped single quote"            | """{"a":'it\\'s'}"""          | [a: "it's"]
        "escape \\n"                      | "{'a':'\\n'}"                 | [a: "\n"]
        "escape \\r"                      | "{'a':'\\r'}"                 | [a: "\r"]
        "escape \\t"                      | "{'a':'\\t'}"                 | [a: "\t"]
        "escape \\\\"                     | "{'a':'\\\\'}  "              | [a: "\\"]
        "escape \\'"                      | "{'a':'\\''}"                 | [a: "'"]
    }

    @Unroll
    def "parseJsonObject misc: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                              | json                          | expected
        "whitespace before first key"     | '{  "a":1}'                   | [a: 1]
        "whitespace between key and :"   | '{"a"  :1}'                   | [a: 1]
        "deeply nested array value"       | '{"a":[[1]]}'                 | [a: [[1]]]
        "unquoted number 1.2.3 → string"  | '{"a":1.2.3}'                 | [a: "1.2.3"]
        "unquoted 12xyz → string"         | '{"a":12xyz}'                 | [a: "12xyz"]
        "unquoted true"                   | '{"a":true}'                  | [a: true]
        "unquoted false"                  | '{"a":false}'                 | [a: false]
        "unquoted null"                   | '{"a":null}'                  | [a: null]
        "digit-prefixed token → string"   | '{"a":123test}'               | [a: "123test"]
    }

    @Unroll
    def "parseJsonArray misc: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                            | json              | expected
        "sq escaped quote"              | "['it\\'s']"      | ["it's"]
        "bare placeholder :age"         | '[:age]'          | [":age"]
        "number failure 1.2.3"          | '[1.2.3]'         | ["1.2.3"]
        "number failure 12xyz"          | '[12xyz]'         | ["12xyz"]
        "deeply nested [[[1]]]"         | '[[[1]]]'         | [[[1]]]
        "whitespace around ints"        | '[ 1 , 2 ]'       | [1, 2]
        "whitespace around strings"     | '["a" , "b"]'     | ["a", "b"]
    }

    @Unroll
    def "parseJsonObject unquoted token edge cases: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                              | json                          | expected
        "truthy → string"                 | '{"a":truthy}'                | [a: "truthy"]
        "falsy → string"                  | '{"a":falsy}'                 | [a: "falsy"]
        "default escape \\x in sq value"  | "{'a':'\\x'}"                 | [a: "x"]
    }

    @Unroll
    def "parseJsonArray escape sequences: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                        | json             | expected
        "trailing comma [1,]"       | '[1,]'           | [1]
        "trailing comma [1 , ]"     | '[1 , ]'         | [1]
        "dq backslash"              | '["a\\\\b"]'     | ["a\\b"]
        "dq \\r"                    | '["a\\rb"]'      | ["a\rb"]
        "dq \\t"                    | '["a\\tb"]'      | ["a\tb"]
        "dq unknown \\x"            | '["a\\xb"]'      | ["axb"]
        "sq backslash"              | "['a\\\\b']"     | ["a\\b"]
        "sq \\n"                    | "['a\\nb']"      | ["a\nb"]
        "sq \\r"                    | "['a\\rb']"      | ["a\rb"]
        "sq \\t"                    | "['a\\tb']"      | ["a\tb"]
        "sq escaped quote"          | "['a\\'b']"      | ["a'b"]
        "sq unknown \\x"            | "['a\\xb']"      | ["axb"]
    }


    // NC 101/104: top-of-loop whitespace skip is unreachable (trim+comma-skip covers it)
    // NC 237: startsWith(":")/startsWith("$mn_qp:") in t/f branch impossible (always starts t/f)
    // NC 275-281: true/false/null/placeholder checks in else(number) branch unreachable (earlier dispatch consumes them)
    @Unroll
    def "parseJsonObject object values: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                                             | json                       | expected
        "whitespace-separated pairs (NC 101/104)"        | '{"a": 1, "b": 2}'         | [a: 1, b: 2]
        "empty object shortcut"                          | '{}'                       | [:]
        "true (NC 237)"                                  | '{"active": true}'         | [active: true]
        "false (NC 237)"                                 | '{"active": false}'        | [active: false]
        "t/f unrecognised word t → string"               | '{"status": tomorrow}'     | [status: "tomorrow"]
        "t/f unrecognised word f → string"               | '{"flag": finished}'       | [flag: "finished"]
        "integer (NC 275-281)"                           | '{"n": 42}'                | [n: 42]
        "double"                                         | '{"n": 3.14}'              | [n: 3.14d]
        "negative integer"                               | '{"n": -7}'                | [n: -7]
        "malformed double → string"                      | '{"v": 1.2.3}'             | [v: "1.2.3"]
        "digits+letters → string"                        | '{"v": 42abc}'             | [v: "42abc"]
        // NC 556: catch(Exception) in extractProjectionField unreachable — no test possible
        // PC 112/113: original keeps raw backslash in dq key (will differ post-refactor)
        "sq value simple"                                | "{'key': 'hello'}"         | [key: "hello"]
        // PC 246
        "bare :param"                                    | '{"name": :title}'         | [name: ":title"]
        "bare :param with digits"                        | '{"id": :id123}'           | [id: ":id123"]
        // PC 253
        "positional placeholder \$mn_qp:0"              | '{"name": $mn_qp:0}'       | [name: '$mn_qp:0']
        // c=='n' branch
        "null value"                                     | '{"val": null}'            | [val: null]
        // nested structures
        "nested object"                                  | '{"a": {"b": 1}}'          | [a: [b: 1]]
        "array inside object"                            | '{"items": [1, 2, 3]}'     | [items: [1, 2, 3]]
        "deeply nested object"                           | '{"a": {"b": {"c": 42}}}'  | [a: [b: [c: 42]]]
    }

    def "dq key: escape sequence unescaped (PC 112/113)"() {
        expect:
        (parser.parseJson('{"ke\\"y": "val"}') as Map).containsKey('ke"y')
    }

    def "sq key: escape sequence unescaped (PC 121/122)"() {
        expect:
        (parser.parseJson("{'ke\\'y': 'val'}") as Map).containsKey("ke'y")
    }

    // PC 129: unquoted keys
    @Unroll
    def "parseJsonObject unquoted keys: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                              | json                   | expected
        "up to colon"                     | '{name: "Alice"}'      | [name: "Alice"]
        "whitespace before colon"         | '{name : "Bob"}'       | [name: "Bob"]
    }

    // PC 156/159: escape sequences in dq object values
    @Unroll
    def "dq object value escape \\#esc (PC 156/159)"() {
        expect:
        parser.parseJson('{"k": "' + raw + '"}') == [k: expected]

        where:
        esc       | raw      | expected
        '"'       | '\\"'    | '"'
        '\\\\'    | '\\\\'   | '\\'
        'n'       | '\\n'    | '\n'
        'r'       | '\\r'    | '\r'
        't'       | '\\t'    | '\t'
        'unknown' | '\\z'    | 'z'
    }

    // PC 180/183: escape sequences in sq object values
    @Unroll
    def "sq object value escape \\#esc (PC 180/183)"() {
        expect:
        parser.parseJson("{'k': '" + raw + "'}") == [k: expected]

        where:
        esc       | raw     | expected
        "'"       | "\\'"   | "'"
        '\\\\'    | '\\\\'  | '\\'
        'n'       | '\\n'   | '\n'
        'r'       | '\\r'   | '\r'
        't'       | '\\t'   | '\t'
        'unknown' | '\\z'   | 'z'
    }

    // PC 341/344: escape sequences in dq array elements
    @Unroll
    def "dq array element escape \\#esc (PC 341/344)"() {
        expect:
        parser.parseJson('["' + raw + '"]') == [expected]

        where:
        esc       | raw      | expected
        '"'       | '\\"'    | '"'
        '\\\\'    | '\\\\'   | '\\'
        'n'       | '\\n'    | '\n'
        'r'       | '\\r'    | '\r'
        't'       | '\\t'    | '\t'
        'unknown' | '\\z'    | 'z'
    }

    // PC 365/368: escape sequences in sq array elements
    @Unroll
    def "sq array element escape \\#esc (PC 365/368)"() {
        expect:
        parser.parseJson("['${raw}']") == [expected]

        where:
        esc       | raw     | expected
        "'"       | "\\'"   | "'"
        '\\\\'    | '\\\\'  | '\\'
        'n'       | '\\n'   | '\n'
        'r'       | '\\r'   | '\r'
        't'       | '\\t'   | '\t'
        'unknown' | '\\z'   | 'z'
    }

    // PC 420/422/426: -/:/$ chars in array literal branch; $ placeholder commented out (known gap)
    @Unroll
    def "parseJsonArray literal branch: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                        | json              | expected
        "negative numbers"                  | '[-1, -42]'           | [-1, -42]
        "named param (: branch)"            | '[:param]'            | [':param']
        "positional placeholder \$mn_qp:0"  | '[$mn_qp:0]'          | ['$mn_qp:0']
        "doubles"                           | '[1.5, 2.7]'          | [1.5d, 2.7d]
        "true/false/null"                   | '[true,false,null]'   | [true, false, null]
        "nested arrays ([ branch)"          | '[[1,2],[3,4]]'       | [[1, 2], [3, 4]]
        "nested object in array ({ branch)" | '[{"a":{"b":1}}]'     | [[a: [b: 1]]]
        "array of objects"                  | '[{"a":1},{"b":2}]'   | [[a: 1], [b: 2]]
    }

    // ─── PC 547 — extractProjectionField instanceof Map false branch ──────────────
    // A {-prefixed query always produces a Map from parseJson, so the false branch
    // (parsed instanceof Map == false) is genuinely unreachable in practice.
    // The array-input guard (non-{ prefix) hits the earlier null-return instead.

    @Unroll
    def "extractProjectionField: #desc"() {
        expect:
        parser.extractProjectionField(input) == expected

        where:
        desc                                    | input                                       | expected
        "array input → null"                    | '[{"$project": "name"}]'                    | null
        "\$project string → field name"         | '{"$project": "name", "active": true}'      | "name"
        "\$project non-string → null"           | '{"$project": 42}'                          | null
        "no \$project key → null"              | '{"active": true}'                          | null
        "null input → null"                     | null                                        | null
        "non-Map from parseJson → null (L528)"  | "{hello"                                    | null
    }

    @Unroll
    def "hasProjection: #desc"() {
        expect:
        parser.hasProjection(input) == expected

        where:
        desc                              | input                    | expected
        "\$project present → true"        | '{"$project": "name"}'   | true
        "\$project absent → false"        | '{"active": true}'       | false
    }

    @Unroll
    def "extractFilterMap: #desc"() {
        expect:
        parser.extractFilterMap(input) == expected

        where:
        desc                              | input                                              | expected
        "plain map → returned directly"   | [active: true]                                     | [active: true]
        "pipeline with \$match"           | [[('$match'): [active: true]]]                     | [active: true]
        "pipeline without \$match → {}"  | [[('$sort'): [name: 1]]]                           | [:]
        "null → null"                     | null                                               | null
        "non-map non-list → null"         | "anything"                                         | null
        "non-map stage skipped → {}"      | [42]                                               | [:]
    }

    @Unroll
    def "parseJsonObject edge cases: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                                          | json                     | expected
        "missing colon after unquoted key (L131)"     | '{a 1}'                  | [a: 1]
        "backslash at end of dq value (L151)"         | '{"a":"test\\}'          | [a: "test}"]
        "backslash at end of sq value (L175)"         | "{'a':'test\\}"          | [a: "test}"]
        "t/f value with colon (L224)"                 | '{"a":t:est}'            | [a: "t:est"]
        "bare param with leading colons (L236)"       | '{"a":::title}'          | [a: "::title"]
        "placeholder with dollar inside (L243)"       | '{"a":$mn_qp:$0}'        | [a: '$mn_qp:$0']
        "number-like value with colon (L259)"         | '{"a":1:2}'              | [a: "1:2"]
        "number-like value with dollar (L260)"        | '{"a":1$2}'              | [a: '1$2']
    }

    @Unroll
    def "parseJsonArray unclosed/colon edge cases: #desc"() {
        expect:
        parser.parseJson(json) == expected

        where:
        desc                                     | json          | expected
        "dq unclosed (L323)"                     | '["test]'     | ["test]"]
        "dq backslash at end (L326)"             | '["test\\]'   | ["test]"]
        "sq unclosed (L347)"                     | "['test]"     | ["test]"]
        "sq backslash at end (L350)"             | "['test\\]"   | ["test]"]
        "value with colon in else branch (L404)" | '[1:2]'       | ["1:2"]
    }
}
