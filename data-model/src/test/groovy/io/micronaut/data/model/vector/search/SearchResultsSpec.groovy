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
                "SearchResult[entity=book, score=Score[value=0.25], similarity=null]]]"
    }

    void "search results reject null input"() {
        when:
        SearchResults.of(null)

        then:
        thrown(NullPointerException)
    }
}
