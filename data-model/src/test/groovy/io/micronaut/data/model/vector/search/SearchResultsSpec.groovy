package io.micronaut.data.model.vector.search

import spock.lang.Specification

class SearchResultsSpec extends Specification {

    void "search results are immutable and iterable"() {
        given:
        def result = new SearchResult<>("book", new Score(0.25d))
        def source = [result]

        when:
        def results = SearchResults.of(source)
        source.clear()

        then:
        results.results() == [result]
        results.iterator().toList() == [result]
        results.toString() == "SearchResults[results=[" +
                "SearchResult[entity=book, score=Score[value=0.25], similarity=null, highlights={}]]]"
    }

    void "search results expose total hits and max score"() {
        given:
        def low = new SearchResult<>("a", new Score(0.1d))
        def high = new SearchResult<>("b", new Score(0.9d), [title: ["<b>b</b>"]])

        expect:
        SearchResults.of([low, high]).totalHits() == 2
        SearchResults.of([low, high], 40).totalHits() == 40
        SearchResults.of([low, high]).maxScore() == new Score(0.9d)
        SearchResults.of([]).maxScore() == null
        high.highlights() == [title: ["<b>b</b>"]]
        low.highlights().isEmpty()
    }

    void "search results reject null input"() {
        when:
        SearchResults.of(null)

        then:
        thrown(NullPointerException)
    }
}
