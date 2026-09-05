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
import jakarta.persistence.criteria.JoinType
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
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
    // G2: aliased terminal — R1Book.title is stored as "book_title" (@MappedProperty).
    //     The resolver must use getPersistedName() on the terminal property, not the property name.
    // G3: MANY_TO_ONE FK field match.
    //     findByAuthorId emits the FK field "author" at compile time (identity access shortcut);
    //     the resolver must identify this as a direct FK filter, not route it through a sub-query.
    // G4: two-hop MANY_TO_ONE chain — Review -> Book -> Author.
    //     Requires the resolver to recurse correctly: sub-query on Book filtered by author.name,
    //     then sub-query results used to filter Review.book.
    // G5: single-hop forward MANY_TO_ONE traversal (regression).
    void "mapped terminal property participates in countDistinct"() {
        given:
        def author = authorRepository.save(new R1Author("Herbert"))
        bookRepository.saveAll([
                new R1Book("Dune", author),
                new R1Book("Dune", author),
                new R1Book("Foundation", author)
        ])

        expect:
        bookRepository.countDistinctTitle() == 2L
    }

    // G6: plain field is not mis-classified as an association path (regression).
    //     findByTitle must resolve "book_title" directly without routing through association logic.

    // A nested INNER join has to hold at every hop: a review whose book carries no author is
    // excluded by review -> book -> author, even though review -> book matches.
    void "a nested INNER criteria join requires a match at every hop"() {
        given:
        def author = authorRepository.save(new R1Author("Nested author"))
        def authored = bookRepository.save(new R1Book("Authored book", author))
        def unauthored = bookRepository.save(new R1Book("Unauthored book", null))
        reviewRepository.save(new R1Review("Review of authored", authored))
        reviewRepository.save(new R1Review("Review of unauthored", unauthored))

        when:
        PredicateSpecification<R1Review> nestedInner = { root, cb ->
            root.join("book", JoinType.INNER).join("author", JoinType.INNER)
            cb.like(root.get("text"), "Review of%")
        }
        def results = reviewRepository.findAll(nestedInner)

        then:
        results*.text == ["Review of authored"]
    }
}
