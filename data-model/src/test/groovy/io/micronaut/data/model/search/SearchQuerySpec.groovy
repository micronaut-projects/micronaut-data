package io.micronaut.data.model.search

import spock.lang.Specification

class SearchQuerySpec extends Specification {

    void "builder captures every option"() {
        when:
        def query = SearchQuery.builder("micronaut data")
                .fields("title", "body")
                .mode(MatchMode.ALL)
                .boost("title", 2.0d)
                .highlight("body")
                .minScore(0.2d)
                .language("simple")
                .build()

        then:
        query.query == "micronaut data"
        query.fields == ["title", "body"] as Set
        query.mode == MatchMode.ALL
        query.fuzziness == 0
        query.boosts == [title: 2.0d]
        query.highlightFields == ["body"] as Set
        query.minScore == 0.2d
        query.language == "simple"
        query.terms*.text() == ["micronaut", "data"]
    }

    void "fuzzy sets the mode"() {
        expect:
        SearchQuery.builder("coffe").fuzzy(1).build().mode == MatchMode.FUZZY
        SearchQuery.builder("coffe").fuzzy(1).build().fuzziness == 1
        SearchQuery.of("coffee").mode == MatchMode.ANY
        SearchQuery.of("coffee").fields.empty
    }

    void "a fuzzy edit distance outside 0 to 2 is rejected"() {
        when:
        SearchQuery.builder("coffe").fuzzy(maxEdits)

        then:
        thrown(IllegalArgumentException)

        where:
        maxEdits << [-1, 3]
    }

    void "fuzziness is dropped when another mode replaces fuzzy"() {
        when:
        def query = SearchQuery.builder("coffee").fuzzy(2).mode(MatchMode.ANY).build()

        then:
        query.mode == MatchMode.ANY
        query.fuzziness == 0
        query == SearchQuery.of("coffee")
        query.hashCode() == SearchQuery.of("coffee").hashCode()
    }

    void "queries are value objects"() {
        expect:
        SearchQuery.builder("a").fields("x").build() == SearchQuery.builder("a").fields("x").build()
        SearchQuery.builder("a").fields("x").build() != SearchQuery.builder("a").fields("y").build()
    }

    void "candidates is bounded"() {
        when:
        SearchQuery.builder("q").candidates(value)

        then:
        thrown(IllegalArgumentException)

        where:
        value << [0, SearchQuery.MAX_CANDIDATES + 1]
    }

    void "toString leaves the search string out"() {
        expect:
        SearchQuery.of("secret words").toString() == "SearchQuery{queryLength=12, fields=[], mode=ANY}"
    }
}
