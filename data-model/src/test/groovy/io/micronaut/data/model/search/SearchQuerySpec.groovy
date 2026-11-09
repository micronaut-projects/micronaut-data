package io.micronaut.data.model.search

import io.micronaut.data.model.vector.Vector
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

    void "a minimum score that is negative, infinite or NaN is rejected"() {
        when:
        SearchQuery.builder("coffee").minScore(minScore)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("The minimum score must be a finite number of 0 or more")

        where:
        minScore << [-0.1d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY]
    }

    void "a minimum score of zero is accepted"() {
        expect:
        SearchQuery.builder("coffee").minScore(0d).build().minScore == 0d
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

    void "a boost must be a finite number above 0"() {
        when:
        SearchQuery.builder("a").boost("title", boost)

        then:
        thrown(IllegalArgumentException)

        where:
        boost << [0d, -1d, Double.NaN, Double.POSITIVE_INFINITY]
    }

    void "hybrid weights must be finite and not negative"() {
        when:
        SearchQuery.builder("a").textWeight(weight)

        then:
        thrown(IllegalArgumentException)

        when:
        SearchQuery.builder("a").vectorWeight(weight)

        then:
        thrown(IllegalArgumentException)

        where:
        weight << [-1d, Double.NaN, Double.POSITIVE_INFINITY]
    }

    void "a hybrid query needs one positive weight"() {
        when:
        SearchQuery.builder("a").vector("embedding", Vector.of([1f, 0f] as float[])).textWeight(0).vectorWeight(0).build()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("positive textWeight or vectorWeight")
    }

    void "a zero weight is accepted when the other one is positive or the query is not hybrid"() {
        given:
        def vector = Vector.of([1f, 0f] as float[])

        expect:
        SearchQuery.builder("a").vector("embedding", vector).textWeight(0).build().textWeight == 0d
        SearchQuery.builder("a").vector("embedding", vector).vectorWeight(0).build().vectorWeight == 0d
        !SearchQuery.builder("a").textWeight(0).vectorWeight(0).build().hybrid
    }

    void "toString leaves the search string out"() {
        expect:
        SearchQuery.of("secret words").toString() == "SearchQuery{queryLength=12, fields=[], mode=ANY}"
    }
}
