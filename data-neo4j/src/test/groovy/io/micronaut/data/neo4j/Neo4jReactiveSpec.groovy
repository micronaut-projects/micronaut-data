package io.micronaut.data.neo4j

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.exceptions.NonUniqueResultException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.graph.tck.entities.Article
import io.micronaut.data.graph.tck.entities.Collaboration
import io.micronaut.data.graph.tck.entities.Colleague
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.neo4j.features.Assignment
import io.micronaut.data.neo4j.features.Audit
import io.micronaut.data.neo4j.features.Engineer
import io.micronaut.data.neo4j.features.Ensemble
import io.micronaut.data.neo4j.features.Pet
import io.micronaut.data.neo4j.features.Project
import io.micronaut.data.neo4j.features.ReactiveArticleRepository
import io.micronaut.data.neo4j.features.ReactiveColleagueRepository
import io.micronaut.data.neo4j.features.ReactiveEngineerRepository
import io.micronaut.data.neo4j.features.ReactiveEnsembleRepository
import io.micronaut.data.neo4j.features.ReactiveMovieRepository
import io.micronaut.data.neo4j.features.ReactiveMovieService
import io.micronaut.data.neo4j.features.Player
import io.micronaut.data.neo4j.features.ReactivePetRepository
import io.micronaut.data.neo4j.features.ReactiveShelfRepository
import io.micronaut.data.neo4j.features.ReactiveTeamRepository
import io.micronaut.data.neo4j.features.Review
import io.micronaut.data.neo4j.features.Seat
import io.micronaut.data.neo4j.features.Shelf
import io.micronaut.data.neo4j.features.Tag
import io.micronaut.data.neo4j.features.Team
import io.micronaut.data.neo4j.features.Volume
import io.micronaut.data.neo4j.operations.Neo4jReactorRepositoryOperations
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.data.neo4j.session.Neo4jReactorConnectionOperations
import io.micronaut.data.neo4j.transaction.Neo4jReactorTransactionOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicReference

@MicronautTest(transactional = false)
class Neo4jReactiveSpec extends Specification implements TestPropertyProvider {

    @Inject
    ReactiveMovieRepository movieRepository

    @Inject
    ReactivePetRepository petRepository

    @Inject
    ReactiveArticleRepository articleRepository

    @Inject
    ReactiveTeamRepository teamRepository

    @Inject
    ReactiveShelfRepository shelfRepository

    @Inject
    ReactiveEngineerRepository engineerRepository

    @Inject
    ReactiveEnsembleRepository ensembleRepository

    @Inject
    ReactiveColleagueRepository colleagueRepository

    @Inject
    ReactiveMovieService movieService

    @Inject
    Neo4jRepositoryOperations operations

    @Inject
    Neo4jReactorRepositoryOperations reactiveOperations

    @Inject
    Neo4jReactorTransactionOperations transactionOperations

    @Inject
    Neo4jReactorConnectionOperations connectionOperations

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

    void "save immutable records that reference each other reactively"() {
        given: "a team whose player refers back to the team instance being saved"
        def players = []
        def team = new Team(null, "Wanderers", players)
        players << new Player(null, "Ada", team)

        when:
        def saved = teamRepository.save(team).block()

        then:
        saved.id() != null
        saved.players()*.id().every { it != null }
        teamRepository.findByName("Wanderers").block().players()*.name() == ["Ada"]
        count("MATCH (p:Player)-[:PLAYS_FOR]->(t:Team) RETURN count(t)") == 1
        count("MATCH (t:Team) RETURN count(t)") == 1
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

    void "a dynamic relationship to a node of an unmapped label survives load, modify and save reactively"() {
        given:
        operations.executeQuery("CREATE (:Pet {name: 'Dino'})-[:OWNS]->(:Unmapped {name: 'bone'})", [:])

        when:
        def dino = petRepository.findById("Dino").block()

        then:
        dino.related.OWNS == [[name: "bone"]]

        when:
        dino.set('legs', 4)
        def saved = petRepository.update(dino).block()

        then:
        saved.related.OWNS == [[name: "bone"]]
        petRepository.findById("Dino").block().get('legs') == 4L
        operations.executeQuery("MATCH (:Pet {name: 'Dino'})-[:OWNS]->(u:Unmapped) RETURN u.name", [:], String) == ["bone"]
    }

    void "reactive find all with a paged query collects dynamic relationships"() {
        given:
        def dino = new Pet("Dino")
        dino.related = [PLAYS_WITH: [new Pet("Hoppy")]]
        petRepository.save(dino).block()

        when:
        def pets = reactiveOperations.findAll(pagedPets()).collectList().block()

        then:
        pets*.name == ["Dino", "Hoppy"]
        pets[0].related.PLAYS_WITH*.name == ["Hoppy"]
    }

    private static PagedQuery<Pet> pagedPets() {
        return new PagedQuery<Pet>() {
            @Override
            Class<Pet> getRootEntity() {
                return Pet
            }

            @Override
            Pageable getPageable() {
                return Pageable.from(0, 10, Sort.of(Sort.Order.asc("name")))
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

    void "finding one reactively fails when there are several rows"() {
        given:
        movieRepository.saveAll([
            new Movie("Speed", "Get ready for rush hour", 1994),
            new Movie("Forrest Gump", "Life is like a box of chocolates", 1994)
        ]).collectList().block()

        when:
        movieRepository.findByReleased(1994).block()

        then:
        thrown(NonUniqueResultException)

        expect: "a limit, one row or none is not ambiguous"
        movieRepository.findFirstByReleased(1994).block().released == 1994
        movieRepository.findByTitle("Speed").block().released == 1994
        movieRepository.findByReleased(1950).blockOptional().empty
    }

    void "a relationship entity without an identifier is written, read and written again reactively"() {
        given:
        def ensemble = new Ensemble("Quartet", [new Seat(1, new Project("first violin"))])

        when:
        def saved = ensembleRepository.save(ensemble).block()

        then:
        saved.seats() == [new Seat(1, new Project("first violin"))]
        operations.executeQuery('MATCH (:Ensemble {name: "Quartet"})-[s:SEATS]->(:Project {name: "first violin"}) RETURN s.chair', [:], Long) == [1L]

        when:
        def found = ensembleRepository.findByName("Quartet").block()

        then:
        found.seats() == [new Seat(1, new Project("first violin"))]

        when: "the same two nodes are the same relationship, as there is no identifier to tell them apart"
        ensembleRepository.update(new Ensemble("Quartet", [new Seat(2, new Project("first violin"))])).block()

        then:
        operations.executeQuery('MATCH (:Ensemble {name: "Quartet"})-[s:SEATS]->(:Project) RETURN s.chair', [:], Long) == [2L]
    }

    void "replacing a single valued relationship entity reactively leaves one relationship"() {
        given:
        def project = new Project("Apollo")
        def ada = new Engineer("Ada")
        ada.assignment = new Assignment(null, "developer", project)
        ada = engineerRepository.save(ada).block()
        def saved = ada.assignment

        when: "the relationship is saved again"
        ada.assignment = new Assignment(saved.id(), "senior developer", project)
        ada = engineerRepository.update(ada).block()

        then:
        ada.assignment.id() == saved.id()
        assignmentTitles() == ["senior developer"]

        when: "it is replaced by one that was not saved"
        ada.assignment = new Assignment(null, "lead", project)
        ada = engineerRepository.update(ada).block()

        then:
        ada.assignment.id() != saved.id()
        assignmentTitles() == ["lead"]

        when: "it moves to another node"
        ada.assignment = new Assignment(ada.assignment.id(), "architect", new Project("Gemini"))
        engineerRepository.update(ada).block()

        then:
        assignmentTitles() == ["architect"]
        operations.executeQuery("MATCH (:Engineer)-[:ASSIGNED_TO]->(p:Project) RETURN p.name", [:], String) == ["Gemini"]
    }

    private List<String> assignmentTitles() {
        return operations.executeQuery("MATCH (:Engineer {name: 'Ada'})-[r:ASSIGNED_TO]->(:Project) RETURN r.title", [:], String)
    }

    void "replacing a single valued relationship entity with an assigned identifier reactively leaves one relationship"() {
        given:
        def project = new Project("Apollo")
        def ada = new Engineer("Ada")
        ada.lastReview = new Review("r1", "good", project)
        engineerRepository.save(ada).block()

        when:
        ada.lastReview = new Review("r2", "better", project)
        engineerRepository.update(ada).block()

        then:
        relationshipValues("LAST_REVIEWED") == [["r2", "better"]]

        when: "the same relationship is saved again"
        ada.lastReview = new Review("r2", "best", project)
        engineerRepository.update(ada).block()

        then:
        relationshipValues("LAST_REVIEWED") == [["r2", "best"]]
    }

    void "relationship entities with assigned identifiers between the same nodes stay separate reactively"() {
        given:
        def project = new Project("Apollo")
        def ada = new Engineer("Ada")
        ada.reviews = [new Review("r1", "good", project), new Review("r2", "bad", project)]

        when:
        engineerRepository.save(ada).block()

        then:
        relationshipValues("REVIEWED") == [["r1", "good"], ["r2", "bad"]]

        when: "they are saved again"
        ada.reviews = [new Review("r1", "great", project), new Review("r2", "bad", project)]
        engineerRepository.update(ada).block()

        then:
        relationshipValues("REVIEWED") == [["r1", "great"], ["r2", "bad"]]
    }

    private List<Object> relationshipValues(String type) {
        return operations.executeQuery("MATCH (:Engineer)-[r:" + type + "]->(:Project) RETURN [r.id, r.verdict] ORDER BY r.id", [:], List)
    }

    void "relationship entities with generated UUIDs between the same nodes stay separate reactively"() {
        given:
        def project = new Project("Apollo")
        def ada = new Engineer("Ada")
        ada.audits = [new Audit(null, "first", project), new Audit(null, "second", project)]

        when:
        ada = engineerRepository.save(ada).block()

        then:
        ada.audits*.id().every { it != null }
        ada.audits*.id().toSet().size() == 2
        def stored = operations.executeQuery("MATCH (:Engineer)-[r:AUDITED]->(:Project) RETURN r.id", [:], String)
        stored.toSet() == ada.audits*.id()*.toString().toSet()
        stored.size() == 2
    }

    void "a relationship entity with an assigned identifier needs one reactively"() {
        given:
        def ada = new Engineer("Ada")
        ada.reviews = [new Review(null, "good", new Project("Apollo"))]

        when:
        engineerRepository.save(ada).block()

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Review")
        e.message.contains("assigned")
    }

    void "new undirected relationships between the same nodes stay separate reactively"() {
        given:
        def ada = new Colleague("Ada")
        def grace = new Colleague("Grace")
        ada.colleagues = [new Collaboration("compiler", grace), new Collaboration("cobol", grace)]

        when:
        def saved = colleagueRepository.save(ada).block().colleagues

        then:
        saved*.id.every { it != null }
        saved*.id.toSet().size() == 2
        collaborations() == 2

        when: "another one that was not saved is added from the other end"
        grace.colleagues = [new Collaboration("flow-matic", new Colleague("Ada"))]
        colleagueRepository.save(grace).block()

        then:
        collaborations() == 3

        when: "one that was saved is saved again from the other end"
        def again = new Collaboration("cobol 60", new Colleague("Ada"))
        again.id = saved[1].id
        grace.colleagues = [again]
        def resaved = colleagueRepository.save(grace).block().colleagues[0]

        then:
        resaved.id == saved[1].id
        collaborations() == 3
    }

    private long collaborations() {
        return count("MATCH (:Colleague)-[r:WORKS_WITH]->(:Colleague) RETURN count(r)")
    }

    void "replacing a single valued undirected relationship entity reactively leaves one relationship"() {
        given:
        def ada = new Colleague("Ada")
        ada.mentor = new Collaboration("first", new Colleague("Grace"))
        ada = colleagueRepository.save(ada).block()

        when: "it is replaced by one that was not saved"
        ada.mentor = new Collaboration("second", new Colleague("Grace"))
        ada = colleagueRepository.update(ada).block()

        then:
        mentorships() == ["second"]

        when: "it moves to another node"
        ada.mentor = new Collaboration("third", new Colleague("Hopper"))
        colleagueRepository.update(ada).block()

        then:
        mentorships() == ["third"]
    }

    private List<String> mentorships() {
        return operations.executeQuery("MATCH (:Colleague)-[r:MENTORS]->(:Colleague) RETURN r.project", [:], String)
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

    void "cancelling a reactive transaction rolls it back"() {
        given:
        def transaction = new AtomicReference<ReactiveTransactionStatus>()

        when: "the subscriber cancels after a write"
        def first = transactionOperations.withTransaction(TransactionDefinition.DEFAULT, status -> {
            transaction.set(status)
            return movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994))
                .flux()
                .concatWith(Flux.never())
        }).take(1).blockLast()

        then:
        first.title == "Speed"
        new PollingConditions(timeout: 10).eventually {
            assert transaction.get().isCompleted()
        }
        movieRepository.count().block() == 0
    }

    void "cancelling a reactive transaction while a query streams rolls it back"() {
        given:
        def transaction = new AtomicReference<ReactiveTransactionStatus>()

        when:
        def first = transactionOperations.withTransaction(TransactionDefinition.DEFAULT, status -> {
            transaction.set(status)
            return movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994))
                .thenMany(reactiveOperations.executeQuery("UNWIND range(1, 100000) AS i RETURN i", [:], Long))
        }).take(1).blockLast()

        then:
        first == 1L
        new PollingConditions(timeout: 10).eventually {
            assert transaction.get().isCompleted()
        }
        movieRepository.count().block() == 0
    }

    void "a query after a commit on the same connection runs outside the transaction"() {
        when:
        def count = connectionOperations.withConnectionMono(ConnectionDefinition.DEFAULT, connection ->
            transactionOperations.withTransaction(TransactionDefinition.DEFAULT, status ->
                movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994))
            ).then(movieRepository.count())
        ).block()

        then:
        count == 1
    }

    void "a query after a rollback on the same connection runs outside the transaction"() {
        when:
        def count = connectionOperations.withConnectionMono(ConnectionDefinition.DEFAULT, connection ->
            transactionOperations.withTransaction(TransactionDefinition.DEFAULT, status ->
                movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994))
                    .then(Mono.error(new IllegalStateException("Rolled back")))
            ).then(movieRepository.count())
                .onErrorResume(IllegalStateException, e -> movieRepository.count())
        ).block()

        then:
        count == 0
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

    void "a new versioned related node with an assigned identifier is inserted reactively"() {
        given:
        def volume = new Volume("v1", "978-1")

        when:
        shelfRepository.save(new Shelf(null, "Graphs", [volume])).block()

        then:
        volume.version == 0L
        operations.executeQuery("MATCH (v:Volume) RETURN v.version AS version", [:])*.asMap()*.get('version') == [0L]
    }

    void "a reactive insert of a versioned node with a taken identifier fails"() {
        given:
        articleRepository.save(new Article("graphs", "Graphs")).block()

        when:
        articleRepository.save(new Article("graphs", "Other")).block()

        then:
        thrown(EntityExistsException)
        articleRepository.findById("graphs").block().title == "Graphs"
    }
}
