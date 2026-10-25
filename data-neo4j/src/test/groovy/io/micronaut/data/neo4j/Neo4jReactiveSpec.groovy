package io.micronaut.data.neo4j

import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.graph.tck.entities.Article
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.neo4j.features.Pet
import io.micronaut.data.neo4j.features.ReactiveArticleRepository
import io.micronaut.data.neo4j.features.ReactiveMovieRepository
import io.micronaut.data.neo4j.features.ReactiveMovieService
import io.micronaut.data.neo4j.features.ReactiveMovieSpecificationRepository
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.neo4j.features.ReactivePetRepository
import io.micronaut.data.neo4j.features.Tag
import io.micronaut.data.neo4j.operations.Neo4jReactorRepositoryOperations
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.data.neo4j.transaction.Neo4jReactorTransactionOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.TransactionDefinition
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification

@MicronautTest(transactional = false)
class Neo4jReactiveSpec extends Specification implements TestPropertyProvider {

    @Inject
    ReactiveMovieRepository movieRepository

    @Inject
    ReactivePetRepository petRepository

    @Inject
    ReactiveArticleRepository articleRepository

    @Inject
    ReactiveMovieSpecificationRepository movieSpecificationRepository

    @Inject
    ReactiveMovieService movieService

    @Inject
    Neo4jRepositoryOperations operations

    @Inject
    Neo4jReactorRepositoryOperations reactiveOperations

    @Inject
    Neo4jReactorTransactionOperations transactionOperations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    void setup() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "reactive operations run on the reactive driver"() {
        expect:
        operations.reactive().is(reactiveOperations)
    }

    void "create, read, update and delete reactively"() {
        when:
        def saved = movieRepository.saveAll([
            new Movie("Speed", "Get ready for rush hour", 1994),
            new Movie("The Matrix", "Welcome to the Real World", 1999)
        ]).collectList().block()

        then:
        saved*.title == ["Speed", "The Matrix"]
        movieRepository.count().block() == 2
        movieRepository.existsById("Speed").block()
        !movieRepository.existsById("Jaws").block()
        movieRepository.findById("The Matrix").block().released == 1999
        movieRepository.findById("Jaws").blockOptional().empty
        movieRepository.findAll().collectList().block()*.title.sort() == ["Speed", "The Matrix"]
        movieRepository.findByReleasedGreaterThan(1995).collectList().block()*.title == ["The Matrix"]

        when:
        def matrix = movieRepository.findById("The Matrix").block()
        matrix.votes = 42
        movieRepository.update(matrix).block()

        then:
        movieRepository.findById("The Matrix").block().votes == 42

        when:
        movieRepository.deleteById("Speed").block()

        then:
        movieRepository.count().block() == 1

        when:
        movieRepository.deleteAll().block()

        then:
        movieRepository.count().block() == 0
    }

    void "save relationships reactively"() {
        given:
        def keanu = new Person("Keanu Reeves", 1964)
        def lana = new Person("Lana Wachowski", 1965)
        def matrix = new Movie("The Matrix", "Welcome to the Real World", 1999)
        matrix.roles = [new Role(["Neo"], keanu)]
        matrix.directors = [lana]

        when:
        def saved = movieRepository.save(matrix).block()

        then:
        saved.roles[0].id != null
        saved.roles[0].person.id != null
        saved.directors[0].id != null
        count("MATCH (:Person {name: 'Keanu Reeves'})-[r:ACTED_IN {roles: ['Neo']}]->(:Movie {title: 'The Matrix'}) RETURN count(r)") == 1
        count("MATCH (:Person {name: 'Lana Wachowski'})-[r:DIRECTED]->(:Movie {title: 'The Matrix'}) RETURN count(r)") == 1

        when: "saving again updates the related nodes rather than creating new ones"
        movieRepository.update(saved).block()

        then:
        count("MATCH (p:Person) RETURN count(p)") == 2
    }

    void "save dynamic relationships and labels reactively"() {
        given:
        def dino = new Pet("Dino")
        dino.labels = ["Dinosaur"] as Set
        dino.set('legs', 4)
        dino.related = [PLAYS_WITH: [new Pet("Hoppy")], TAGGED: [new Tag("prehistoric")]]

        when:
        petRepository.save(dino).block()
        def found = petRepository.findById("Dino").block()

        then:
        found.labels == ["Dinosaur"] as Set
        found.get('legs') == 4L
        found.related.PLAYS_WITH*.name == ["Hoppy"]
        found.related.TAGGED[0] instanceof Tag
    }

    void "stream the results of explicit Cypher"() {
        given:
        movieRepository.saveAll([
            new Movie("Speed", "Get ready for rush hour", 1994),
            new Movie("The Matrix", "Welcome to the Real World", 1999)
        ]).blockLast()

        expect:
        reactiveOperations.executeQuery("MATCH (m:Movie) WHERE m.released > \$year RETURN m", [year: 1990], Movie)
            .map(Movie::getTitle)
            .collectList()
            .block()
            .sort() == ["Speed", "The Matrix"]
        reactiveOperations.executeQuery("MATCH (m:Movie) RETURN count(m) AS total", [:])
            .blockFirst()
            .get("total") == 2L
    }

    void "commit a reactive transaction"() {
        when:
        def count = transactionOperations.withTransaction(TransactionDefinition.DEFAULT, status ->
            movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994)).then(movieRepository.count())
        ).blockLast()

        then:
        count == 1
        movieRepository.count().block() == 1
    }

    void "roll back a reactive transaction"() {
        when:
        transactionOperations.withTransaction(TransactionDefinition.DEFAULT, status ->
            movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994))
                .then(Mono.error(new IllegalStateException("Rolled back")))
        ).blockLast()

        then:
        thrown(IllegalStateException)
        movieRepository.count().block() == 0
    }

    void "transactional reactive methods"() {
        when:
        def count = movieService.saveAndCount(new Movie("Speed", "Get ready for rush hour", 1994)).block()

        then:
        count == 1

        when:
        movieService.saveAndFail(new Movie("The Matrix", "Welcome to the Real World", 1999)).block()

        then:
        thrown(IllegalStateException)
        movieRepository.count().block() == 1
    }

    private long count(String query) {
        return operations.executeQuery(query, [:])[0].get(0) as long
    }

    void "reactive pages, sorting, batch updates and deletes"() {
        given:
        movieRepository.saveAll([
            new Movie("Speed", "Get ready for rush hour", 1994),
            new Movie("The Matrix", "Welcome to the Real World", 1999),
            new Movie("Jaws", "Don't go in the water", 1975)
        ]).collectList().block()

        expect:
        movieRepository.findByTitle("Jaws").block().released == 1975
        movieRepository.findByTitle("Alien").blockOptional().empty
        movieRepository.findAll(Sort.of(Sort.Order.desc("released"))).collectList().block()*.title == ["The Matrix", "Speed", "Jaws"]

        when:
        def page = movieRepository.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.asc("title")))).block()

        then:
        page.content*.title == ["Jaws", "Speed"]
        page.totalSize == 3

        when:
        def movies = movieRepository.findAll().collectList().block()
        movies.each { it.votes = 7 }
        movieRepository.updateAll(movies).collectList().block()

        then:
        movieRepository.findAll().collectList().block()*.votes == [7, 7, 7]

        when:
        movieRepository.delete(movieRepository.findByTitle("Jaws").block()).block()
        movieRepository.deleteAll([movieRepository.findByTitle("Speed").block()]).block()

        then:
        movieRepository.findAll().collectList().block()*.title == ["The Matrix"]

        when:
        movieRepository.deleteAll().block()

        then:
        movieRepository.count().block() == 0
    }

    void "reactive updates and deletes check the @Version property"() {
        given:
        articleRepository.save(new Article("graphs", "Graphs")).block()
        def stale = articleRepository.findById("graphs").block()
        def staleForDelete = articleRepository.findById("graphs").block()
        def current = articleRepository.findById("graphs").block()
        current.title = "Current"

        when:
        def updated = articleRepository.update(current).block()

        then:
        updated.version == 1L

        when:
        stale.title = "Stale"
        articleRepository.update(stale).block()

        then:
        thrown(OptimisticLockException)

        when:
        articleRepository.delete(staleForDelete).block()

        then:
        thrown(OptimisticLockException)
        articleRepository.findById("graphs").block().title == "Current"

        when:
        articleRepository.delete(articleRepository.findById("graphs").block()).block()

        then:
        !articleRepository.existsById("graphs").block()
    }

    void "reactive criteria specifications"() {
        given:
        movieRepository.saveAll([
            new Movie("Speed", "Get ready for rush hour", 1994),
            new Movie("The Matrix", "Welcome to the Real World", 1999)
        ]).collectList().block()
        def after1995 = { root, cb -> cb.greaterThan(root.get("released"), 1995) } as PredicateSpecification<Movie>

        expect:
        movieSpecificationRepository.findAll(after1995).collectList().block()*.title == ["The Matrix"]
        movieSpecificationRepository.count(after1995).block() == 1
        movieSpecificationRepository.exists(after1995).block()
        movieSpecificationRepository.findOne(after1995).block().title == "The Matrix"
    }
}
