package io.micronaut.data.document.mongodb.search

import groovy.transform.Memoized
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.search.tck.AbstractTextSearchSpec

/**
 * Runs the portable search TCK against MongoDB Search ({@code $search}) on the Atlas local deployment.
 */
class MongoAtlasTextSearchSpec extends AbstractTextSearchSpec implements MongoAtlasLocal {

    @Memoized
    @Override
    def getBookSearchRepository() {
        return context.getBean(MongoAtlasBookSearchRepository)
    }

    @Override
    def newBook(String title, String description, String author, int pages) {
        return new AtlasBook(title, description, author, pages)
    }

    @Override
    void awaitIndexing() {
        awaitSearchIndex()
    }

    @Override
    String noStemmingLanguage() {
        // the analyzer is fixed per index
        return null
    }

    @Override
    boolean supportsCursoredPaging() {
        return false
    }

    @Override
    Set<SearchCapability> capabilities() {
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY,
                SearchCapability.FIELD_SCOPE, SearchCapability.HIGHLIGHT, SearchCapability.MIN_SCORE,
                SearchCapability.VECTOR, SearchCapability.HYBRID)
    }

    void "fuzzy matching tolerates one edit"() {
        expect:
        bookSearchRepository.findByTitleSearchFuzzy("kotln")*.title == ["Programming Kotlin"]
        bookSearchRepository.search(SearchQuery.builder("concurency").mode(MatchMode.FUZZY).build()).results()*.entity()*.title == ["Java Concurrency in Practice"]
    }

    void "the index weights boost the title over the description"() {
        when: "a term in the title of one book and the description of another"
        def results = bookSearchRepository.search("rust").results()

        then:
        results*.entity()*.title == ["The Rust Programming Language"]

        when:
        def coffee = bookSearchRepository.search("coffee beans").results()

        then:
        coffee*.entity()*.title == ["Cooking with Coffee"]
        coffee[0].score().value() > 0
    }
}
