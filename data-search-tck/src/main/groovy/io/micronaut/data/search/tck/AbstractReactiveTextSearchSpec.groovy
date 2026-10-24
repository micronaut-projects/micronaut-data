package io.micronaut.data.search.tck

import io.micronaut.context.ApplicationContext
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository

import java.util.concurrent.TimeUnit
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The reactive full-text search test kit: a backend passes it by implementing
 * {@link ReactorBookSearchRepository}.
 */
abstract class AbstractReactiveTextSearchSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    abstract ReactorBookSearchRepository getBookSearchRepository()

    abstract AsyncBookSearchRepository getAsyncBookSearchRepository()

    void setup() {
        bookSearchRepository.deleteAll().block()
        bookSearchRepository.saveAll([
                new Book("The Rust Programming Language", "Learn systems programming with Rust", "Klabnik", 560),
                new Book("Programming Kotlin", "Kotlin for the JVM and beyond", "Subramaniam", 460),
                new Book("Java Concurrency in Practice", "Threads and locks for the Java programmer", "Goetz", 400),
                new Book("Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean", 120),
        ]).collectList().block()
    }

    void cleanup() {
        bookSearchRepository.deleteAll().block()
    }

    void "a reactive search returns the hits ordered by relevance"() {
        when:
        def results = bookSearchRepository.searchBySearch("programming").block()

        then:
        results.results().size() == 2
        results.results()*.entity()*.title as Set == ["The Rust Programming Language", "Programming Kotlin"] as Set
        results.results().every { it.score().value() > 0 }
        results.results()[0].score().value() >= results.results()[1].score().value()
        bookSearchRepository.searchBySearch("nothingmatchesthis").block().results().isEmpty()
    }

    void "reactive finders and counts use the search keyword"() {
        expect:
        bookSearchRepository.findByTitleSearch("java").collectList().block()*.title == ["Java Concurrency in Practice"]
        bookSearchRepository.countBySearch("programming").block() == 2L
        bookSearchRepository.countBySearch("nothingmatchesthis").block() == 0L
    }

    void "reactive CRUD round trip"() {
        when:
        def saved = bookSearchRepository.save(new Book("Baking Bread", "Sourdough for the home baker", "Dough", 220)).block()

        then:
        saved.id != null
        bookSearchRepository.findById(saved.id).block().title == "Baking Bread"
        bookSearchRepository.count().block() == 5L

        when:
        saved.title = "Baking Sourdough"
        bookSearchRepository.update(saved).block()

        then:
        bookSearchRepository.findById(saved.id).block().title == "Baking Sourdough"

        when:
        bookSearchRepository.deleteById(saved.id).block()

        then:
        bookSearchRepository.findById(saved.id).blockOptional().isEmpty()
        bookSearchRepository.count().block() == 4L
    }

    void "the reactive SearchRepository searches, pages and counts"() {
        when:
        def results = bookSearchRepository.search("programming").block()

        then:
        results.results()*.entity()*.title as Set == ["The Rust Programming Language", "Programming Kotlin"] as Set
        results.results()[0].score().value() >= results.results()[1].score().value()
        bookSearchRepository.search("programming", Pageable.from(0, 1)).block().results().size() == 1
        bookSearchRepository.countHits("programming").block() == 2L
        bookSearchRepository.countHits("nothingmatchesthis").block() == 0L

        when:
        def programmatic = bookSearchRepository.search(SearchQuery.builder("programming").fields("title").build()).block()

        then:
        programmatic.results()*.entity()*.title as Set == ["The Rust Programming Language", "Programming Kotlin"] as Set
        bookSearchRepository.search(SearchQuery.of("nothingmatchesthis")).block().results().isEmpty()
        bookSearchRepository.search(SearchQuery.of("programming"), Pageable.from(0, 1)).block().results().size() == 1
    }

    void "the async SearchRepository searches and counts"() {
        expect:
        asyncBookSearchRepository.search("java").get(30, TimeUnit.SECONDS).results()*.entity()*.title == ["Java Concurrency in Practice"]
        asyncBookSearchRepository.search(SearchQuery.of("java")).get(30, TimeUnit.SECONDS).results().size() == 1
        asyncBookSearchRepository.search(SearchQuery.of("programming"), Pageable.from(0, 1)).get(30, TimeUnit.SECONDS).results().size() == 1
        asyncBookSearchRepository.countHits("programming").get(30, TimeUnit.SECONDS) == 2L
    }
}
