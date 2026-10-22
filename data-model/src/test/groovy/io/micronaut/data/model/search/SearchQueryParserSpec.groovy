package io.micronaut.data.model.search

import spock.lang.Specification
import spock.lang.Unroll

class SearchQueryParserSpec extends Specification {

    @Unroll
    void "parses #query"() {
        expect:
        SearchQueryParser.parse(query).collect { group -> group.collect { term -> (term.negated() ? '-' : '') + (term.phrase() ? '"' + term.text() + '"' : term.text()) } } == expected

        where:
        query                                | expected
        null                                 | []
        ""                                   | []
        "   "                                | []
        "coffee"                             | [["coffee"]]
        "bake coffee cake"                   | [["bake", "coffee", "cake"]]
        "bake coffee -cake"                  | [["bake", "coffee", "-cake"]]
        '"ken burns" baseball -sport'        | [['"ken burns"', "baseball", "-sport"]]
        '-"ken burns" baseball'              | [['-"ken burns"', "baseball"]]
        '"single"'                           | [["single"]]
        '"unterminated phrase'               | [['"unterminated phrase"']]
        "tea OR coffee"                      | [["tea"], ["coffee"]]
        "black tea OR green coffee -decaf"   | [["black", "tea"], ["green", "coffee", "-decaf"]]
        "OR coffee OR"                       | [["coffee"]]
        "a - b"                              | [["a", "-", "b"]]
        "  spaced   out  "                   | [["spaced", "out"]]
    }

    void "terms flattens the groups"() {
        expect:
        SearchQueryParser.terms("tea OR coffee -decaf")*.text() == ["tea", "coffee", "decaf"]
    }

    void "match mode keywords round trip"() {
        expect:
        MatchMode.values().every { MatchMode.fromKeyword(it.keyword()) == it }
        MatchMode.fromKeyword("Search") == MatchMode.ANY
        MatchMode.fromKeyword("All") == MatchMode.ALL
        MatchMode.fromKeyword("Contains") == null
        TextSearchSupport.parseKeyword("TitleSearchAll").propertyName() == "Title"
        TextSearchSupport.parseKeyword("TitleSearchAll").mode() == MatchMode.ALL
        TextSearchSupport.parseKeyword("Search").propertyName() == ""
        TextSearchSupport.parseKeyword("TitleContains") == null
    }
}
