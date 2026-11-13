package io.micronaut.data.document.mongodb.search

import groovy.transform.Memoized
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.data.search.tck.repositories.BookSearchRepository

class MongoTextSearchSpec extends AbstractTextSearchSpec implements MongoTestPropertyProvider {

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(MongoBookSearchRepository)
    }

    @Override
    boolean supportsCursoredPaging() {
        // MongoDB repositories page by offset only
        return false
    }

    @Override
    Set<SearchCapability> capabilities() {
        // $text searches the whole text index and has no prefix matching
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.LANGUAGE_OVERRIDE)
    }

    void "an unsorted pageable keeps the relevance order, best match first"() {
        given:
        // saved weakest match first, which MongoDB returns out of relevance order when nothing sorts
        bookSearchRepository.saveAll([
                new Book("Gardening Notes", "Soil water plants seeds compost and a cup of tea", "Green", 90),
                new Book("Tea Leaves", "Tea", "Leaf", 40),
                new Book("Herbal Tea", "Mint and chamomile tea blends", "Herb", 60),
        ])

        when:
        def all = bookSearchRepository.searchBySearch("tea", Pageable.unpaged())
        def first = bookSearchRepository.search("tea", Pageable.from(0, 2))
        def second = bookSearchRepository.search("tea", Pageable.from(1, 2))
        def scores = all.results()*.score()*.value()

        then:
        all.results()*.entity()*.title == ["Tea Leaves", "Herbal Tea", "Gardening Notes"]
        scores == scores.toSorted().reverse()
        first.results()*.entity()*.title == ["Tea Leaves", "Herbal Tea"]
        second.results()*.entity()*.title == ["Gardening Notes"]
    }

    void "SearchAll matches every term literally, without stemming"() {
        expect:
        bookSearchRepository.searchByTitleSearchAll("programming language").results()*.entity()*.title == ["The Rust Programming Language"]
        bookSearchRepository.searchByTitleSearchAll("programming languages").results().isEmpty()
        bookSearchRepository.search("languages").results()*.entity()*.title == ["The Rust Programming Language"]
    }
}
