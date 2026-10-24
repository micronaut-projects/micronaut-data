package io.micronaut.data.model.runtime.convert

import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.search.MatchMode
import spock.lang.Specification
import spock.lang.Unroll

class PostgresTsQueryConverterSpec extends Specification {

    @Unroll
    void "renders '#query' as #mode"() {
        expect:
        PostgresTsQueryConverter.render(query, mode) == expected

        where:
        query                          | mode             | expected
        "coffee"                       | MatchMode.ANY    | "'coffee'"
        "bake coffee cake"             | MatchMode.ANY    | "'bake' | 'coffee' | 'cake'"
        "bake coffee cake"             | MatchMode.ALL    | "'bake' & 'coffee' & 'cake'"
        "bake coffee -cake"            | MatchMode.ANY    | "('bake' | 'coffee') & !'cake'"
        "bake coffee -cake"            | MatchMode.ALL    | "'bake' & 'coffee' & !'cake'"
        "coffee -cake"                 | MatchMode.ANY    | "'coffee' & !'cake'"
        '"ken burns" baseball'         | MatchMode.ANY    | "'ken burns' | 'baseball'"
        '"ken burns" -baseball'        | MatchMode.ALL    | "'ken burns' & !'baseball'"
        "tea OR coffee -decaf"         | MatchMode.ANY    | "('tea') | ('coffee' & !'decaf')"
        "tea OR coffee"                | MatchMode.ALL    | "('tea') | ('coffee')"
        "mic dat"                      | MatchMode.PREFIX | "'mic':* & 'dat':*"
        '"exact words" mic'            | MatchMode.PREFIX | "'exact words' & 'mic':*"
        "the fat rats"                 | MatchMode.PHRASE | "'the fat rats'"
        '"quoted phrase"'              | MatchMode.PHRASE | "'quoted phrase'"
        "o'reilly"                     | MatchMode.ANY    | "'o''reilly'"
        "fat & rat | !cat"             | MatchMode.ANY    | "'fat' | '&' | 'rat' | '|' | '!cat'"
        ""                             | MatchMode.ANY    | "'mn_no_match_0'"
        "-only"                        | MatchMode.ANY    | "'mn_no_match_0'"
        null                           | MatchMode.PHRASE | "'mn_no_match_0'"
    }

    void "converters are bound per mode"() {
        expect:
        PostgresTsQueryConverter.converterFor(MatchMode.ANY) == PostgresTsQueryConverter.Any.name
        PostgresTsQueryConverter.converterFor(MatchMode.ALL) == PostgresTsQueryConverter.All.name
        PostgresTsQueryConverter.converterFor(MatchMode.PHRASE) == PostgresTsQueryConverter.Phrase.name
        PostgresTsQueryConverter.converterFor(MatchMode.PREFIX) == PostgresTsQueryConverter.Prefix.name
        new PostgresTsQueryConverter.All().convertToPersistedValue("a b", ConversionContext.DEFAULT) == "'a' & 'b'"
        new PostgresTsQueryConverter.All().convertToEntityValue("x", ConversionContext.DEFAULT) == "x"
        PostgresTsQueryConverter.converterFor(MatchMode.FUZZY) == PostgresTsQueryConverter.FuzzyPositive.name
        PostgresTsQueryConverter.fuzzyConverterFor(true) == PostgresTsQueryConverter.FuzzyNegated.name
    }
}
