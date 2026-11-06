package io.micronaut.data.model.runtime.convert

import io.micronaut.data.model.search.MatchMode
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.data.model.runtime.convert.OracleTextQueryConverter.Part.FULL
import static io.micronaut.data.model.runtime.convert.OracleTextQueryConverter.Part.NEGATED
import static io.micronaut.data.model.runtime.convert.OracleTextQueryConverter.Part.POSITIVE

class OracleTextQueryConverterSpec extends Specification {

    @Unroll
    void "renders '#query' as #mode #part"() {
        expect:
        OracleTextQueryConverter.render(query, mode, part) == expected

        where:
        query                      | mode             | part     | expected
        "coffee"                   | MatchMode.ANY    | FULL     | "{coffee}"
        "bake coffee cake"         | MatchMode.ANY    | FULL     | "{bake} | {coffee} | {cake}"
        "bake coffee cake"         | MatchMode.ALL    | FULL     | "{bake} & {coffee} & {cake}"
        "bake coffee -cake"        | MatchMode.ANY    | FULL     | "({bake} | {coffee}) ~ {cake}"
        "bake -coffee -cake"       | MatchMode.ALL    | FULL     | "(({bake}) ~ {coffee}) ~ {cake}"
        "bake coffee -cake"        | MatchMode.ANY    | POSITIVE | "{bake} | {coffee}"
        "bake coffee -cake"        | MatchMode.ANY    | NEGATED  | "{cake}"
        "tea OR coffee -decaf"     | MatchMode.ANY    | NEGATED  | "{decaf}"
        "tea OR coffee -decaf"     | MatchMode.ANY    | FULL     | "({tea}) | (({coffee}) ~ {decaf})"
        '"ken burns" baseball'     | MatchMode.ANY    | FULL     | "{ken burns} | {baseball}"
        "the fat rats"             | MatchMode.PHRASE | FULL     | "{the fat rats}"
        '"quoted" words'           | MatchMode.PHRASE | POSITIVE | "{quoted words}"
        "mic dat"                  | MatchMode.PREFIX | FULL     | "mic% & dat%"
        '"exact words" mic'        | MatchMode.PREFIX | FULL     | "{exact words} & mic%"
        "coffe"                    | MatchMode.FUZZY  | FULL     | "fuzzy({coffe},60,100)"
        "coffe tee"                | MatchMode.FUZZY  | FULL     | "fuzzy({coffe},60,100) | fuzzy({tee},60,100)"
        "near about"               | MatchMode.FUZZY  | FULL     | "fuzzy({near},60,100) | fuzzy({about},60,100)"
        "a_c"                      | MatchMode.PREFIX | FULL     | "ac%"
        "and or not"               | MatchMode.ANY    | FULL     | "{and} | {or} | {not}"
        "a{b}c"                    | MatchMode.ANY    | FULL     | "{abc}"
        ""                         | MatchMode.ANY    | FULL     | "{mn_no_match_0} ~ {mn_no_match_0}"
        "-only"                    | MatchMode.ANY    | FULL     | "{mn_no_match_0} ~ {mn_no_match_0}"
        "coffee"                   | MatchMode.ANY    | NEGATED  | "{mn_no_match_0} ~ {mn_no_match_0}"
        null                       | MatchMode.PHRASE | FULL     | "{mn_no_match_0} ~ {mn_no_match_0}"
        "{}"                       | MatchMode.ANY    | FULL     | "{mn_no_match_0} ~ {mn_no_match_0}"
        "{} coffee"                | MatchMode.ANY    | FULL     | "{coffee}"
        "coffee -{}"               | MatchMode.ANY    | NEGATED  | "{mn_no_match_0} ~ {mn_no_match_0}"
        '"{}"'                     | MatchMode.PHRASE | FULL     | "{mn_no_match_0} ~ {mn_no_match_0}"
        "a"                        | MatchMode.PREFIX | FULL     | "{a}"
        "a mic"                    | MatchMode.PREFIX | FULL     | "{a} & mic%"
    }

    void "converters exist for every mode and part"() {
        expect:
        MatchMode.values().every { OracleTextQueryConverter.converterFor(it, FULL) != null }
        OracleTextQueryConverter.converterFor(MatchMode.ANY, FULL) == OracleTextQueryConverter.Any.name
        OracleTextQueryConverter.converterFor(MatchMode.ALL, POSITIVE) == OracleTextQueryConverter.AllPositive.name
        OracleTextQueryConverter.converterFor(MatchMode.PHRASE, NEGATED) == OracleTextQueryConverter.Negated.name
        new OracleTextQueryConverter.FuzzyPositive().convertToPersistedValue("coffe -x", null) == "fuzzy({coffe},60,100)"
    }
}
