package io.micronaut.data.neo4j

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.data.neo4j.tck.Neo4jMovieRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * The repository operations that batch methods and callers without a query method use.
 */
@MicronautTest(transactional = false)
class Neo4jOperationsSpec extends Specification implements TestPropertyProvider {

    @Inject
    Neo4jMovieRepository movieRepository

    @Inject
    Neo4jRepositoryOperations operations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    void setup() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "save, update and delete in batches"() {
        when:
        def saved = movieRepository.saveAll([
            new Movie("Speed", "Get ready for rush hour", 1994),
            new Movie("The Matrix", "Welcome to the Real World", 1999),
            new Movie("John Wick", "Don't set him off", 2014)
        ])

        then:
        saved*.title == ["Speed", "The Matrix", "John Wick"]
        movieRepository.count() == 3

        when:
        saved.each { it.votes = 7 }
        movieRepository.updateAll(saved)

        then:
        movieRepository.findAll()*.votes == [7L, 7L, 7L]

        when:
        movieRepository.deleteAll(saved.findAll { it.released < 2000 })

        then:
        movieRepository.findAll()*.title == ["John Wick"]

        when:
        movieRepository.deleteAll()

        then:
        movieRepository.count() == 0
    }

    void "find, count, stream and page without a query method"() {
        given:
        movieRepository.saveAll([
            new Movie("Speed", null, 1994),
            new Movie("The Matrix", null, 1999),
            new Movie("John Wick", null, 2014)
        ])
        def all = pagedQuery(Pageable.UNPAGED)
        def firstTwo = pagedQuery(Pageable.from(0, 2, Sort.of(Sort.Order.desc("released"))))

        expect:
        operations.findOne(Movie, "Speed").released == 1994
        operations.findOne(Movie, "Jaws") == null
        operations.count(all) == 3
        operations.findAll(all).collect { it.title }.sort() == ["John Wick", "Speed", "The Matrix"]
        operations.findAll(firstTwo)*.title == ["John Wick", "The Matrix"]
        operations.findStream(firstTwo).map { it.title }.toList() == ["John Wick", "The Matrix"]

        when:
        def page = operations.findPage(firstTwo)

        then:
        page.content*.title == ["John Wick", "The Matrix"]
        page.totalSize == 3
    }

    void "sorting ignores case and rejects unknown properties"() {
        given:
        movieRepository.saveAll([new Movie("b", null, 1), new Movie("A", null, 2), new Movie("c", null, 3)])

        expect:
        operations.findAll(pagedQuery(Pageable.from(0, 10, Sort.of(Sort.Order.asc("title", true)))))*.title == ["A", "b", "c"]

        when:
        operations.findAll(pagedQuery(Pageable.from(0, 10, Sort.of(Sort.Order.asc("rating")))))

        then:
        thrown(IllegalArgumentException)
    }

    private static PagedQuery<Movie> pagedQuery(Pageable pageable) {
        return new PagedQuery<Movie>() {
            @Override
            Class<Movie> getRootEntity() {
                return Movie
            }

            @Override
            Pageable getPageable() {
                return pageable
            }

            @Override
            String getName() {
                return "findAll"
            }

            @Override
            AnnotationMetadata getAnnotationMetadata() {
                return AnnotationMetadata.EMPTY_METADATA
            }
        }
    }
}
