package io.micronaut.data.search.tck

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.search.UnsupportedSearchFeatureException
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.data.search.tck.repositories.BookPhraseSearchRepository
import io.micronaut.data.search.tck.repositories.BookPrefixSearchRepository
import io.micronaut.data.search.tck.repositories.BookSearchRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The shared full-text search test kit. A backend passes the suite by implementing
 * {@link BookSearchRepository} and reporting the {@link SearchCapability capabilities} it supports;
 * tests of unsupported capabilities are skipped.
 */
abstract class AbstractTextSearchSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    abstract BookSearchRepository getBookSearchRepository()

    /**
     * @return The capabilities the backend supports beyond term matching and score ordering
     */
    Set<SearchCapability> capabilities() {
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FIELD_SCOPE, SearchCapability.HIGHLIGHT, SearchCapability.VECTOR)
    }

    /**
     * @return Whether the backend stems words, so that {@code breads} matches {@code bread}
     */
    boolean supportsStemming() {
        return true
    }

    void setup() {
        bookSearchRepository.deleteAll()
        bookSearchRepository.saveAll([
                new Book("The Rust Programming Language", "Learn systems programming with Rust", "Klabnik", 560),
                new Book("Programming Kotlin", "Kotlin for the JVM and beyond", "Subramaniam", 460),
                new Book("Java Concurrency in Practice", "Threads and locks for the Java programmer", "Goetz", 400),
                new Book("Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean", 120),
                new Book("Baking Bread", "Sourdough and yeast breads for the home baker", "Dough", 220),
        ])
    }

    void cleanup() {
        bookSearchRepository.deleteAll()
    }

    void "search matches any term across every search field ordered by relevance"() {
        when:
        def results = bookSearchRepository.search("programming")

        then:
        results.results().size() == 2
        results.totalHits() == 2
        results.results()*.entity()*.title as Set == ["The Rust Programming Language", "Programming Kotlin"] as Set
        results.results().every { it.score().value() > 0 }
        results.results().every { it.similarity() == null }
        results.results().every { it.highlights().isEmpty() }
        results.results()[0].score().value() >= results.results()[1].score().value()
        results.maxScore() == results.results()[0].score()
        // the title and the description of the Rust book both match
        results.results()[0].entity().title == "The Rust Programming Language"
    }

    void "search string grammar supports OR and negation"() {
        expect:
        bookSearchRepository.search("bread OR coffee").results()*.entity()*.title as Set == ["Cooking with Coffee", "Baking Bread"] as Set
        bookSearchRepository.search("coffee -recipes").results().isEmpty()
        bookSearchRepository.search("coffee -sourdough").results()*.entity()*.title == ["Cooking with Coffee"]
    }

    void "search with no match returns empty results"() {
        expect:
        bookSearchRepository.search("nothingmatchesthis").results().isEmpty()
        bookSearchRepository.search("nothingmatchesthis").maxScore() == null
        bookSearchRepository.countHits("nothingmatchesthis") == 0
        bookSearchRepository.search("").results().isEmpty()
    }

    void "countHits counts the matches"() {
        expect:
        bookSearchRepository.countHits("programming") == 2
        bookSearchRepository.countHits("java") == 1
        bookSearchRepository.countBySearchAll("programming kotlin") == 1
        bookSearchRepository.countBySearchAll("programming rust") == 1
    }

    void "search with a pageable limits the results"() {
        when:
        def page = bookSearchRepository.search("programming OR java OR coffee", Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))))

        then:
        page.results().size() == 2
        page.results()*.entity()*.title == ["Cooking with Coffee", "Java Concurrency in Practice"]

        when:
        def all = bookSearchRepository.searchBySearch("programming OR java OR coffee", Pageable.unpaged())

        then:
        all.results().size() == 4
    }

    void "stemming matches inflected words"() {
        assumeTrue(supportsStemming())

        expect:
        bookSearchRepository.search("breads").results()*.entity()*.title == ["Baking Bread"]
        bookSearchRepository.findByTitleSearch("languages")*.title == ["The Rust Programming Language"]
    }

    void "derived finder with the Search keyword matches one property"() {
        expect:
        bookSearchRepository.findByTitleSearch("programming")*.title as Set == ["The Rust Programming Language", "Programming Kotlin"] as Set
    }

    void "a property keyword searches only that property"() {
        assumeTrue(capabilities().contains(SearchCapability.FIELD_SCOPE))

        expect:
        // 'systems' only appears in the description
        bookSearchRepository.findByTitleSearch("systems").isEmpty()
    }

    void "SearchAll requires every term"() {
        when:
        def results = bookSearchRepository.searchByTitleSearchAll("programming language")

        then:
        results.results()*.entity()*.title == ["The Rust Programming Language"]
        results.results()[0].score().value() > 0

        expect:
        bookSearchRepository.searchByTitleSearchAll("programming kotlin").results()*.entity()*.title == ["Programming Kotlin"]
        bookSearchRepository.searchByTitleSearchAll("programming coffee").results().isEmpty()
    }

    void "SearchPhrase requires adjacent terms in order"() {
        assumeTrue(capabilities().contains(SearchCapability.PHRASE))
        def repository = (BookPhraseSearchRepository) bookSearchRepository

        expect:
        repository.searchByDescriptionSearchPhrase("coffee beans").results()*.entity()*.title == ["Cooking with Coffee"]
        repository.searchByDescriptionSearchPhrase("beans coffee").results().isEmpty()
        repository.searchByDescriptionSearchPhrase("systems programming").results()*.entity()*.title == ["The Rust Programming Language"]
    }

    void "SearchPrefix matches the start of words"() {
        assumeTrue(capabilities().contains(SearchCapability.PREFIX))
        def repository = (BookPrefixSearchRepository) bookSearchRepository

        expect:
        repository.findByTitleSearchPrefix("prog")*.title as Set == ["The Rust Programming Language", "Programming Kotlin"] as Set
        repository.findByTitleSearchPrefix("kot")*.title == ["Programming Kotlin"]
        repository.findByTitleSearchPrefix("programming kot")*.title == ["Programming Kotlin"]
        repository.findByTitleSearchPrefix("xyz").isEmpty()
    }

    void "search predicates combine with ordinary predicates"() {
        expect:
        bookSearchRepository.findBySearchAndAuthor("programming", "Klabnik")*.title == ["The Rust Programming Language"]
        bookSearchRepository.findBySearchAndAuthor("programming", "Nobody").isEmpty()
        bookSearchRepository.findByTitleSearchAndPagesGreaterThan("programming", 500)*.title == ["The Rust Programming Language"]
        bookSearchRepository.findBySearchOrderByPages("programming OR java")*.title == ["Java Concurrency in Practice", "Programming Kotlin", "The Rust Programming Language"]
    }

    void "search with a SearchQuery searches every field ordered by relevance"() {
        when:
        def results = bookSearchRepository.search(SearchQuery.of("programming"))

        then:
        results.results()*.entity()*.title as Set == ["The Rust Programming Language", "Programming Kotlin"] as Set
        results.totalHits() == 2
        results.results().every { it.score().value() > 0 }
        results.results()[0].score().value() >= results.results()[1].score().value()
        results.results()[0].entity().title == "The Rust Programming Language"
        bookSearchRepository.search(SearchQuery.of("nothingmatchesthis")).results().isEmpty()
    }

    void "search with a SearchQuery restricts the fields and the match mode"() {
        assumeTrue(capabilities().contains(SearchCapability.FIELD_SCOPE))

        expect:
        // 'systems' only appears in the description
        bookSearchRepository.search(SearchQuery.builder("systems").fields("title").build()).results().isEmpty()
        bookSearchRepository.search(SearchQuery.builder("systems").fields("description").build()).results()*.entity()*.title == ["The Rust Programming Language"]
        bookSearchRepository.search(SearchQuery.builder("programming language").mode(MatchMode.ALL).build()).results()*.entity()*.title == ["The Rust Programming Language"]
        bookSearchRepository.search(SearchQuery.builder("programming coffee").mode(MatchMode.ALL).build()).results().isEmpty()
    }

    void "search with a SearchQuery honours the phrase and prefix modes"() {
        expect:
        !capabilities().contains(SearchCapability.PHRASE) ||
            bookSearchRepository.search(SearchQuery.builder("coffee beans").mode(MatchMode.PHRASE).build()).results()*.entity()*.title == ["Cooking with Coffee"]
        !capabilities().contains(SearchCapability.PHRASE) ||
            bookSearchRepository.search(SearchQuery.builder("beans coffee").mode(MatchMode.PHRASE).build()).results().isEmpty()
        !capabilities().contains(SearchCapability.PREFIX) ||
            bookSearchRepository.search(SearchQuery.builder("kot").mode(MatchMode.PREFIX).build()).results()*.entity()*.title == ["Programming Kotlin"]
    }

    void "search with a SearchQuery and a pageable limits the results"() {
        when:
        def page = bookSearchRepository.search(SearchQuery.of("programming OR java OR coffee"), Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))))

        then:
        page.results().size() == 2
        page.results()*.entity()*.title == ["Cooking with Coffee", "Java Concurrency in Practice"]

        when:
        def best = bookSearchRepository.search(SearchQuery.of("programming OR java OR coffee"), Pageable.from(0, 1))

        then:
        best.results().size() == 1
        best.results()[0].score().value() > 0
    }

    void "search with a SearchQuery drops hits below the minimum score"() {
        given:
        def all = bookSearchRepository.search(SearchQuery.of("programming"))
        def top = all.results()[0].score().value()

        expect:
        bookSearchRepository.search(SearchQuery.builder("programming").minScore(top).build()).results()*.entity()*.title == ["The Rust Programming Language"]
        bookSearchRepository.search(SearchQuery.builder("programming").minScore(top * 10 + 1).build()).results().isEmpty()
    }

    void "search with a SearchQuery rejects options the operation cannot honour"() {
        when:
        bookSearchRepository.search(SearchQuery.builder("programming").boost("title", 2).build())

        then:
        thrown(UnsupportedSearchFeatureException)

        when:
        bookSearchRepository.search(SearchQuery.builder("programming").language("simple").build())

        then:
        thrown(UnsupportedSearchFeatureException)
    }

    void "search with a SearchQuery highlights the matched terms of the requested fields"() {
        assumeTrue(capabilities().contains(SearchCapability.HIGHLIGHT))

        when:
        def results = bookSearchRepository.search(SearchQuery.builder("programming").highlight("title").build())

        then:
        results.results().size() == 2
        results.results().every { it.highlights().keySet() == ["title"] as Set }
        results.results().every { it.highlights()["title"].size() == 1 }
        results.results().every { it.highlights()["title"][0].contains("<em>Programming</em>") }
        results.results().every { !it.highlights()["title"][0].contains("<em>Rust</em>") }

        when:
        def both = bookSearchRepository.search(SearchQuery.builder("rust").highlight("title", "description").build())

        then:
        both.results()*.entity()*.title == ["The Rust Programming Language"]
        both.results()[0].highlights()["title"][0].contains("<em>Rust</em>")
        both.results()[0].highlights()["description"][0].contains("<em>Rust</em>")
        // the entity itself is untouched
        both.results()[0].entity().title == "The Rust Programming Language"

        when:
        def none = bookSearchRepository.search(SearchQuery.builder("programming").build())

        then:
        none.results().every { it.highlights().isEmpty() }
    }

    void "search with a SearchQuery rejects a vector when the store has no vector search"() {
        assumeTrue(!capabilities().contains(SearchCapability.VECTOR))

        when:
        bookSearchRepository.search(SearchQuery.builder("programming").vector("title", Vector.of([1f, 0f, 0f] as float[])).build())

        then:
        thrown(UnsupportedSearchFeatureException)
    }

    void "search with a SearchQuery rejects highlighting when the store cannot highlight"() {
        assumeTrue(!capabilities().contains(SearchCapability.HIGHLIGHT))

        when:
        bookSearchRepository.search(SearchQuery.builder("programming").highlight("title").build())

        then:
        thrown(UnsupportedSearchFeatureException)
    }
}
