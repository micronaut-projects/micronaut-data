package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.City
import io.micronaut.data.nitrite.model.R1Author
import io.micronaut.data.nitrite.model.R1Book
import io.micronaut.data.nitrite.model.R1Review
import io.micronaut.data.nitrite.model.State
import io.micronaut.data.nitrite.repository.CityRepository
import io.micronaut.data.nitrite.repository.R1AuthorRepository
import io.micronaut.data.nitrite.repository.R1BookRepository
import io.micronaut.data.nitrite.repository.R1ReviewRepository
import io.micronaut.data.nitrite.repository.StateRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Gate tests for R1 — metadata-driven association path resolution.
 * G1–G6 per the R1 plan; G7 (embedded) is covered by the existing NitriteEmbeddedSpec.
 */
@MicronautTest(transactional = false)
class NitriteR1PathResolutionSpec extends Specification {

    @Inject StateRepository stateRepository
    @Inject CityRepository cityRepository
    @Inject R1AuthorRepository authorRepository
    @Inject R1BookRepository bookRepository
    @Inject R1ReviewRepository reviewRepository

    def setup() {
        reviewRepository.deleteAll()
        bookRepository.deleteAll()
        authorRepository.deleteAll()
        cityRepository.deleteAll()
        stateRepository.deleteAll()
    }

    // G1: irregular plural ONE_TO_MANY reverse lookup (State.cities — property name "cities",
    //     associated entity simpleName "City", decapitalized "city").
    //     Old heuristic matched by getSimpleName()/getDecapitalizedName(); new resolver must match
    //     by property name "cities" via getPropertyPath.
    void "G1 - irregular plural ONE_TO_MANY reverse lookup resolves by property name"() {
        given:
        def ca = stateRepository.save(new State(name: "California"))
        cityRepository.saveAll([
            new City(name: "Los Angeles",   state: ca),
            new City(name: "San Francisco", state: ca),
        ])

        when:
        def found = stateRepository.findByCitiesName("Los Angeles")

        then:
        found != null
        found.name == "California"
    }

    // G2: aliased terminal — R1Book.title is stored as "book_title" (@MappedProperty).
    //     The resolver must use getPersistedName() on the terminal property, not the property name.
    void "G2 - aliased terminal field in ONE_TO_MANY reverse sub-query resolves by persisted name"() {
        given:
        def herbert = authorRepository.save(new R1Author("Herbert"))
        def asimov  = authorRepository.save(new R1Author("Asimov"))
        bookRepository.save(new R1Book("Dune",       herbert))
        bookRepository.save(new R1Book("Foundation", asimov))

        when:
        def authors = authorRepository.findByBooksTitle("Dune")

        then:
        authors.size() == 1
        authors[0].name == "Herbert"
    }

    // G3: MANY_TO_ONE FK field match.
    //     findByAuthorId emits the FK field "author" at compile time (identity access shortcut);
    //     the resolver must identify this as a direct FK filter, not route it through a sub-query.
    void "G3 - MANY_TO_ONE FK field match returns correct books without sub-query"() {
        given:
        def herbert = authorRepository.save(new R1Author("Herbert"))
        def asimov  = authorRepository.save(new R1Author("Asimov"))
        bookRepository.save(new R1Book("Dune",       herbert))
        bookRepository.save(new R1Book("Foundation", asimov))

        when:
        def books = bookRepository.findByAuthorId(herbert.id)

        then:
        books.size() == 1
        books[0].title == "Dune"
    }

    // G4: two-hop MANY_TO_ONE chain — Review -> Book -> Author.
    //     Requires the resolver to recurse correctly: sub-query on Book filtered by author.name,
    //     then sub-query results used to filter Review.book.
    void "G4 - two-hop MANY_TO_ONE chain resolves correctly"() {
        given:
        def herbert = authorRepository.save(new R1Author("Herbert"))
        def asimov  = authorRepository.save(new R1Author("Asimov"))
        def dune       = bookRepository.save(new R1Book("Dune",       herbert))
        def foundation = bookRepository.save(new R1Book("Foundation", asimov))
        reviewRepository.saveAll([
            new R1Review("Great book", dune),
            new R1Review("Loved it",   dune),
            new R1Review("Classic",    foundation),
        ])

        when:
        def herbertReviews = reviewRepository.findByBookAuthorName("Herbert")
        def asimovReviews  = reviewRepository.findByBookAuthorName("Asimov")

        then:
        herbertReviews.size() == 2
        asimovReviews.size() == 1
        asimovReviews[0].text == "Classic"
    }

    // G5: single-hop forward MANY_TO_ONE traversal (regression).
    void "G5 - single-hop MANY_TO_ONE forward traversal returns correct books"() {
        given:
        def herbert = authorRepository.save(new R1Author("Herbert"))
        def asimov  = authorRepository.save(new R1Author("Asimov"))
        bookRepository.save(new R1Book("Dune",         herbert))
        bookRepository.save(new R1Book("Dune Messiah", herbert))
        bookRepository.save(new R1Book("Foundation",   asimov))

        when:
        def herbertBooks = bookRepository.findByAuthorName("Herbert")

        then:
        herbertBooks.size() == 2
        herbertBooks*.title as Set == ["Dune", "Dune Messiah"] as Set
    }

    // G6: plain field is not mis-classified as an association path (regression).
    //     findByTitle must resolve "book_title" directly without routing through association logic.
    void "G6 - plain field lookup is not mis-classified as an association path"() {
        given:
        def herbert = authorRepository.save(new R1Author("Herbert"))
        bookRepository.save(new R1Book("Dune",       herbert))
        bookRepository.save(new R1Book("Foundation", authorRepository.save(new R1Author("Asimov"))))

        when:
        def book = bookRepository.findByTitle("Dune").orElse(null)

        then:
        book != null
        book.title == "Dune"
    }
}
