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

    void "queries are value objects"() {
        expect:
        SearchQuery.builder("a").fields("x").build() == SearchQuery.builder("a").fields("x").build()
        SearchQuery.builder("a").fields("x").build() != SearchQuery.builder("a").fields("y").build()
    }
}
