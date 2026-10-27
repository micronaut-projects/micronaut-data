package io.micronaut.data.graph.tck

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.graph.annotation.Direction
import io.micronaut.data.graph.tck.entities.Article
import io.micronaut.data.graph.tck.entities.Colleague
import io.micronaut.data.graph.tck.entities.Collaboration
import io.micronaut.data.graph.tck.entities.Friend
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.graph.tck.repositories.ArticleRepository
import io.micronaut.data.graph.tck.repositories.ColleagueRepository
import io.micronaut.data.graph.tck.repositories.FriendRepository
import io.micronaut.data.graph.tck.repositories.MovieQueryRepository
import io.micronaut.data.graph.tck.repositories.MovieRepository
import io.micronaut.data.graph.model.criteria.GraphCriteria
import io.micronaut.data.graph.tck.repositories.MovieSpecificationRepository
import io.micronaut.data.graph.tck.repositories.PersonQueryRepository
import io.micronaut.data.graph.tck.repositories.PersonRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.transaction.TransactionOperations
import spock.lang.Requires
import spock.lang.Specification

/**
 * Behaviour every graph repository implementation supports.
 */
abstract class AbstractGraphRepositorySpec extends Specification {

    abstract MovieRepository getMovieRepository()

    abstract PersonRepository getPersonRepository()

    abstract FriendRepository getFriendRepository()

    abstract ColleagueRepository getColleagueRepository()

    abstract TransactionOperations<?> getTransactionOperations()

    /**
     * The repository of an entity with a {@code @Version} property, or null if the implementation writes nodes
     * through another module that covers optimistic locking.
     *
     * @return The repository
     */
    ArticleRepository getArticleRepository() {
        null
    }

    /**
     * The repository of movies queried with criteria specifications, or null if the implementation does not
     * support them.
     *
     * @return The repository
     */
    MovieSpecificationRepository getMovieSpecificationRepository() {
        null
    }

    /**
     * Removes every node the specs create.
     */
    abstract void cleanupGraph()

    /**
     * Whether saving a node also writes its relationships and related nodes. Oracle property graph repositories
     * write only the rows of their vertex tables.
     *
     * @return True if relationships are written
     */
    boolean supportsRelationshipWrites() {
        true
    }

    /**
     * Whether finders traverse relationships: {@code findByDirectorsName}, criteria specifications that join an
     * association and path checks such as {@code existsPathByMentorBetweenName}. The relationships are written by
     * {@link #saveMovie} and {@link #savePerson}, through the repositories or another way.
     *
     * @return True if relationships can be queried
     */
    boolean supportsRelationshipFinders() {
        supportsRelationshipWrites()
    }

    /**
     * Whether {@code @Join} fetches relationships and related nodes into the entity.
     *
     * @return True if fetch joins are supported
     */
    boolean supportsRelationshipReads() {
        true
    }

    /**
     * Whether finders return paths and relationships: {@code GraphPath} and {@code GraphRelationship} results.
     *
     * @return True if path finders are supported
     */
    boolean supportsPaths() {
        true
    }

    /**
     * Whether the repositories run explicit Cypher queries.
     *
     * @return True if {@code @Query} takes Cypher
     */
    boolean supportsExplicitQueries() {
        true
    }

    /**
     * Whether the repositories run Cypher: explicit Cypher queries, fetch joins and path finders.
     *
     * @return True for Cypher databases
     */
    boolean supportsCypher() {
        supportsRelationshipReads() && supportsPaths() && supportsExplicitQueries()
    }

    /**
     * Saves a movie with its roles and directors. Implementations whose repositories write node rows only
     * override this method to write the people and the relationships another way.
     *
     * @param movie The movie
     * @return The saved movie
     */
    protected Movie saveMovie(Movie movie) {
        movieRepository.save(movie)
    }

    /**
     * Saves a person with the chain of mentors. Implementations whose repositories write node rows only override
     * this method to write the mentors and the relationships another way.
     *
     * @param person The person
     * @return The saved person
     */
    protected Person savePerson(Person person) {
        personRepository.save(person)
    }

    /**
     * Whether criteria can test for a chain of relationships, as {@code GraphCriteria.connected} does. Apache AGE
     * cannot run a variable-length pattern inside {@code EXISTS}.
     *
     * @return True if connected criteria are supported
     */
    boolean supportsConnectedCriteria() {
        true
    }

    void setup() {
        cleanupGraph()
    }

    void cleanup() {
        cleanupGraph()
    }

    private void saveMovies() {
        def keanu = new Person("Keanu Reeves", 1964)
        def carrie = new Person("Carrie-Anne Moss", 1967)
        def lana = new Person("Lana Wachowski", 1965)
        def matrix = new Movie("The Matrix", "Welcome to the Real World", 1999)
        matrix.roles = [new Role(["Neo"], keanu), new Role(["Trinity"], carrie)]
        matrix.directors = [lana]
        def reloaded = new Movie("The Matrix Reloaded", "Free your mind", 2003)
        reloaded.roles = [new Role(["Neo"], keanu)]
        reloaded.directors = [lana]
        def speed = new Movie("Speed", "Get ready for rush hour", 1994)
        speed.roles = [new Role(["Jack Traven"], keanu)]
        saveMovie(matrix)
        saveMovie(reloaded)
        saveMovie(speed)
    }

    void "save and find a node by its assigned identifier"() {
        when:
        movieRepository.save(new Movie("Cloud Atlas", "Everything is connected", 2012))
        def found = movieRepository.findById("Cloud Atlas")

        then:
        found.present
        found.get().description == "Everything is connected"
        found.get().released == 2012
        movieRepository.count() == 1
        movieRepository.existsById("Cloud Atlas")
        !movieRepository.existsById("Unknown")
    }

    @Requires({ instance.supportsRelationshipWrites() })
    void "save generates identifiers for nodes and relationships"() {
        given:
        saveMovies()

        when:
        def keanu = personRepository.findByName("Keanu Reeves")

        then:
        keanu.present
        keanu.get().id != null
        keanu.get().born == 1964
        personRepository.count() == 3
    }

    @Requires({ instance.supportsRelationshipWrites() })
    void "saving the same related node twice does not duplicate it"() {
        given:
        saveMovies()

        expect:
        personRepository.findAll().findAll { it.name == "Keanu Reeves" }.size() == 1
    }

    void "find by criteria on node properties"() {
        given:
        saveMovies()

        expect:
        movieRepository.findByReleasedGreaterThanOrderByReleased(1995)*.title == ["The Matrix", "The Matrix Reloaded"]
        movieRepository.findByTitleContainsIgnoreCase("matrix")*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
        movieRepository.countByReleasedLessThan(2000) == 2
        movieRepository.existsByTitle("Speed")
        !movieRepository.existsByTitle("Point Break")
    }

    @Requires({ instance.supportsRelationshipFinders() })
    void "find by criteria on relationships"() {
        given:
        saveMovies()

        expect:
        movieRepository.findByDirectorsName("Lana Wachowski")*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
        movieRepository.findByRolesPersonName("Keanu Reeves")*.title.sort() == ["Speed", "The Matrix", "The Matrix Reloaded"]
        movieRepository.findByRolesPersonName("Carrie-Anne Moss")*.title == ["The Matrix"]
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsRelationshipReads() })
    void "fetch relationships and relationship properties with joins"() {
        given:
        saveMovies()

        when:
        def matrix = movieRepository.findByTitle("The Matrix").get()

        then:
        matrix.directors*.name == ["Lana Wachowski"]
        matrix.roles.size() == 2
        matrix.roles.find { it.person.name == "Keanu Reeves" }.roles == ["Neo"]
        matrix.roles.every { it.id != null }

        when: "relationships are not joined"
        def unjoined = movieRepository.findById("The Matrix").get()

        then:
        unjoined.roles.isEmpty()
        unjoined.directors.isEmpty()
    }

    @Requires({ instance.supportsRelationshipFinders() })
    void "find nodes by a single relationship"() {
        given:
        def mentor = new Person("Morpheus", 1961)
        def student = new Person("Neo", 1964)
        student.mentor = mentor
        savePerson(student)

        expect:
        personRepository.findByMentorName("Morpheus")*.name == ["Neo"]
        personRepository.findByMentorName("Neo").isEmpty()

        and: "the relationship is not fetched without a join"
        personRepository.findByName("Neo").get().mentor == null
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsRelationshipReads() })
    void "fetch a single relationship"() {
        given:
        def mentor = new Person("Morpheus", 1961)
        def student = new Person("Neo", 1964)
        student.mentor = mentor
        personRepository.save(student)

        expect:
        personRepository.queryByName("Neo").get().mentor.name == "Morpheus"
    }

    void "projections and aggregates"() {
        given:
        saveMovies()

        expect:
        movieRepository.findTitleByReleasedGreaterThanOrderByTitle(1990) == ["Speed", "The Matrix", "The Matrix Reloaded"]
        movieRepository.findMaxReleased() == 2003
        movieRepository.findByReleasedBetweenOrderByReleased(1990, 2000)*.title == ["Speed", "The Matrix"]
    }

    void "update and delete queries"() {
        given:
        saveMovies()

        when:
        movieRepository.updateReleased("Speed", 1995)

        then:
        movieRepository.findById("Speed").get().released == 1995

        when:
        def deleted = movieRepository.deleteByReleasedLessThan(2000)

        then:
        deleted == 2
        movieRepository.findAll()*.title == ["The Matrix Reloaded"]
        !supportsRelationshipFinders() || personRepository.findByName("Keanu Reeves").present
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsRelationshipReads() })
    void "update an entity"() {
        given:
        def neo = personRepository.save(new Person("Neo", 1964))

        when:
        def mentor = new Person("Morpheus", 1961)
        neo.mentor = mentor
        personRepository.update(neo)

        then:
        personRepository.queryByName("Neo").get().mentor.name == "Morpheus"
        personRepository.count() == 2
    }

    @Requires({ instance.supportsRelationshipWrites() })
    void "delete an entity removes its relationships"() {
        given:
        saveMovies()

        when:
        movieRepository.delete(movieRepository.findById("Speed").get())

        then:
        !movieRepository.existsById("Speed")
        movieRepository.findByRolesPersonName("Keanu Reeves").size() == 2
    }

    @Requires({ instance.supportsRelationshipWrites() })
    void "a relationship that cascades PERSIST creates new nodes and links existing nodes without updating them"() {
        given:
        def keanu = personRepository.save(new Person("Keanu Reeves", 1964))

        when: "one acquaintance is a stale copy of the saved node and the other is new"
        def stale = new Person("Keanu Reeves", 1900)
        stale.id = keanu.id
        def carrie = new Person("Carrie-Anne Moss", 1967)
        carrie.acquaintances = [stale, new Person("Laurence Fishburne", 1961)]
        def saved = personRepository.save(carrie)

        then:
        saved.acquaintances*.id.every { it != null }
        personRepository.findByAcquaintancesName("Keanu Reeves")*.name == ["Carrie-Anne Moss"]
        personRepository.findByAcquaintancesName("Laurence Fishburne")*.name == ["Carrie-Anne Moss"]
        personRepository.findByName("Keanu Reeves").get().born == 1964
        personRepository.count() == 3
    }

    @Requires({ instance.supportsRelationshipWrites() })
    void "a relationship that does not cascade links existing nodes only"() {
        given:
        def keanu = personRepository.save(new Person("Keanu Reeves", 1964))

        when: "the endorser is a stale copy of the saved node"
        def stale = new Person("Keanu Reeves", 1900)
        stale.id = keanu.id
        def carrie = new Person("Carrie-Anne Moss", 1967)
        carrie.endorsers = [stale]
        personRepository.save(carrie)

        then: "the relationship is written and the node is left as it was"
        personRepository.findByEndorsersName("Keanu Reeves")*.name == ["Carrie-Anne Moss"]
        personRepository.findByName("Keanu Reeves").get().born == 1964
        personRepository.count() == 2

        when: "the endorser has no identifier"
        def lana = new Person("Lana Wachowski", 1965)
        lana.endorsers = [new Person("Lilly Wachowski", 1967)]
        personRepository.save(lana)

        then:
        def noId = thrown(DataAccessException)
        noId.message.contains("without an identifier")

        and: "the save wrote nothing"
        !personRepository.findByName("Lana Wachowski").present
        personRepository.count() == 2

        when: "the endorser does not exist"
        def ghost = new Person("Nobody", 2000)
        ghost.id = UUID.randomUUID()
        lana.mentor = new Person("Lilly Wachowski", 1967)
        lana.endorsers = [ghost]
        personRepository.save(lana)

        then:
        def missing = thrown(DataAccessException)
        missing.message.contains("does not exist")

        and: "neither the node nor the mentor it cascaded to remain"
        personRepository.count() == 2
    }

    @Requires({ instance.supportsRelationshipWrites() })
    void "a save that fails while writing its relationships writes nothing"() {
        given:
        def movie = new Movie("Bound", "In the end, it's all about the money", 1996)
        movie.roles = [new Role(["Corky"], new Person("Gina Gershon", 1962)), new Role(["Violet"], null)]

        when:
        movieRepository.save(movie)

        then:
        thrown(DataAccessException)
        !movieRepository.existsById("Bound")
        !personRepository.findByName("Gina Gershon").present
    }

    @Requires({ instance.supportsExplicitQueries() })
    void "explicit queries"() {
        given:
        saveMovies()

        when:
        movieRepository.vote("Speed")
        movieRepository.vote("Speed")

        then:
        movieRepository.search("Reloaded")*.title == ["The Matrix Reloaded"]
        movieRepository.findById("Speed").get().votes == 2
    }

    void "page through nodes"() {
        given:
        saveMovies()

        when: "the finder's criteria leave out Speed, released in 1994"
        def page = movieRepository.findByReleasedGreaterThan(1995, Pageable.from(0, 1, Sort.of(Sort.Order.asc("title"))))

        then:
        page.content*.title == ["The Matrix"]
        page.totalSize == 2
        page.totalPages == 2

        when:
        def second = movieRepository.findByReleasedGreaterThan(1995, page.nextPageable())

        then:
        second.content*.title == ["The Matrix Reloaded"]
    }

    private void saveFriends() {
        def fred = new Friend("Fred")
        def barney = new Friend("Barney")
        def joe = new Friend("Joe")
        def wilma = new Friend("Wilma")
        fred.friends = [barney, wilma]
        barney.friends = [joe]
        wilma.friends = [joe]
        friendRepository.save(fred)
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsPaths() })
    void "find the shortest path between two nodes"() {
        given:
        saveFriends()

        when:
        def path = friendRepository.findShortestPath(new Friend("Fred"), new Friend("Joe"))

        then:
        path.present
        path.get().length() == 2
        path.get().start().name == "Fred"
        path.get().end().name == "Joe"
        path.get().segments().size() == 2
        path.get().relationships().every { it.type() == "FRIENDS" }

        when:
        def bounded = friendRepository.findShortestPathByFriends("Fred", "Joe", 1)

        then:
        !bounded.present

        when:
        bounded = friendRepository.findShortestPathByFriends("Fred", "Joe", 3)

        then:
        bounded.present
        bounded.get().nodes()*.name.first() == "Fred"

        when: "a depth above micronaut.data.graph.max-path-depth is asked for"
        friendRepository.findShortestPathByFriends("Fred", "Joe", 11)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("max-path-depth")
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsPaths() })
    void "find all paths and check paths exist"() {
        given:
        saveFriends()

        when:
        def paths = friendRepository.findAllPathsByFriends("Fred", "Joe")

        then:
        paths.size() == 2
        paths*.nodes().collect { it*.name }.sort { it[1] } == [["Fred", "Barney", "Joe"], ["Fred", "Wilma", "Joe"]]
        friendRepository.existsPath("Fred", "Joe")
        !friendRepository.existsPath("Fred", "Nobody")
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsExplicitQueries() })
    void "explicit queries return relationships between nodes of different types"() {
        given:
        saveMovies()

        when:
        def actedIn = movieRepository.findActedIn("The Matrix")

        then:
        actedIn*.from()*.name.sort() == ["Carrie-Anne Moss", "Keanu Reeves"]
        actedIn*.to()*.title == ["The Matrix", "The Matrix"]
        actedIn*.type() == ["ACTED_IN", "ACTED_IN"]
    }

    @Requires({ instance.supportsRelationshipWrites() })
    void "an undirected relationship saved from both ends is stored once"() {
        given:
        def ada = new Colleague("Ada")
        def grace = new Colleague("Grace")
        ada.colleagues = [new Collaboration("compiler", grace)]
        colleagueRepository.save(ada)

        when: "the relationship is saved again from its other end"
        grace.colleagues = [new Collaboration("cobol", new Colleague("Ada"))]
        colleagueRepository.save(grace)

        then: "the relationship is matched in either direction, so its properties are updated rather than a second one created"
        colleagueRepository.countCollaborations() == 1
        colleagueRepository.findByColleaguesProject("cobol")*.name.sort() == ["Ada", "Grace"]
        colleagueRepository.findByColleaguesProject("compiler").isEmpty()
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsPaths() })
    void "find relationships between nodes"() {
        given:
        saveFriends()

        when:
        def relationships = friendRepository.findRelationships("Fred", "Barney")

        then:
        relationships.size() == 1
        relationships[0].type() == "FRIENDS"
        relationships[0].from().name == "Fred"
        relationships[0].to().name == "Barney"
        friendRepository.countRelationshipsByFriends() == 4
    }

    void "a rolled back transaction writes nothing"() {
        when:
        transactionOperations.executeWrite { status ->
            movieRepository.save(new Movie("Johnny Mnemonic", "The hottest data on earth", 1995))
            assert movieRepository.existsById("Johnny Mnemonic")
            status.setRollbackOnly()
            null
        }

        then:
        !movieRepository.existsById("Johnny Mnemonic")
    }

    void "a committed transaction writes"() {
        when:
        transactionOperations.executeWrite { status ->
            movieRepository.save(new Movie("John Wick", "Don't set him off", 2014))
            null
        }

        then:
        movieRepository.existsById("John Wick")
    }

    @Requires({ instance.articleRepository != null })
    void "a @Version property starts at zero and is incremented by each update"() {
        when:
        def article = articleRepository.save(new Article("graphs", "Graphs"))

        then:
        article.version == 0L
        articleRepository.findById("graphs").get().version == 0L

        when:
        def loaded = articleRepository.findById("graphs").get()
        loaded.title = "Graph databases"
        def updated = articleRepository.update(loaded)

        then:
        updated.version == 1L
        with(articleRepository.findById("graphs").get()) {
            title == "Graph databases"
            version == 1L
        }
    }

    @Requires({ instance.articleRepository != null })
    void "updating a stale node fails with an optimistic lock exception"() {
        given:
        articleRepository.save(new Article("graphs", "Graphs"))
        def first = articleRepository.findById("graphs").get()
        def stale = articleRepository.findById("graphs").get()
        first.title = "First"
        articleRepository.update(first)

        when:
        stale.title = "Stale"
        articleRepository.update(stale)

        then:
        thrown(OptimisticLockException)
        with(articleRepository.findById("graphs").get()) {
            title == "First"
            version == 1L
        }
    }

    @Requires({ instance.articleRepository != null })
    void "deleting a stale node fails with an optimistic lock exception"() {
        given:
        articleRepository.save(new Article("graphs", "Graphs"))
        def stale = articleRepository.findById("graphs").get()
        def current = articleRepository.findById("graphs").get()
        current.title = "Current"
        articleRepository.update(current)

        when:
        articleRepository.delete(stale)

        then:
        thrown(OptimisticLockException)
        articleRepository.existsById("graphs")

        when:
        articleRepository.delete(articleRepository.findById("graphs").get())

        then:
        !articleRepository.existsById("graphs")
    }

    @Requires({ instance.articleRepository != null })
    void "saveAll and updateAll write many versioned nodes"() {
        when:
        def saved = articleRepository.saveAll([new Article("a", "A"), new Article("b", "B"), new Article("c", "C")])

        then:
        saved*.version == [0L, 0L, 0L]
        articleRepository.count() == 3

        when:
        def loaded = articleRepository.findAll().sort { it.slug }
        def stale = articleRepository.findById("b").get()
        loaded.each { it.title = it.title.toLowerCase() }
        def updated = articleRepository.updateAll(loaded)

        then:
        updated*.version == [1L, 1L, 1L]
        articleRepository.findAll()*.title.sort() == ["a", "b", "c"]

        when: "one node of a batch was changed since it was read"
        def again = articleRepository.findAll().sort { it.slug }
        again[1] = stale
        articleRepository.updateAll(again)

        then:
        thrown(OptimisticLockException)
    }

    private static PredicateSpecification<Movie> releasedAfter(int year) {
        return { root, cb -> cb.greaterThan(root.get("released"), year) } as PredicateSpecification<Movie>
    }

    @Requires({ instance.movieSpecificationRepository != null })
    void "find nodes with criteria specifications"() {
        given:
        saveMovies()
        def specs = movieSpecificationRepository

        expect:
        specs.findAll(releasedAfter(1995))*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
        specs.findAll(releasedAfter(1995), Sort.of(Sort.Order.desc("released")))*.title == ["The Matrix Reloaded", "The Matrix"]
        specs.findOne({ root, cb -> cb.equal(root.get("title"), "Speed") } as PredicateSpecification<Movie>).get().released == 1994
        !specs.findOne({ root, cb -> cb.equal(root.get("title"), "Jaws") } as PredicateSpecification<Movie>).present
        specs.count(releasedAfter(1995)) == 2
        specs.count((PredicateSpecification<Movie>) null) == 3
        specs.exists(releasedAfter(2000))
        !specs.exists(releasedAfter(2010))
        specs.findAll(PredicateSpecification.where(releasedAfter(1990))
            .and({ root, cb -> cb.lessThan(root.get("released"), 2000) } as PredicateSpecification<Movie>))*.title.sort() == ["Speed", "The Matrix"]

        when:
        def page = specs.findAll(releasedAfter(1990), Pageable.from(0, 2, Sort.of(Sort.Order.asc("released"))))

        then:
        page.content*.title == ["Speed", "The Matrix"]
        page.totalSize == 3
    }

    @Requires({ instance.movieSpecificationRepository != null && instance.supportsRelationshipFinders() })
    void "criteria specifications follow relationships"() {
        given:
        saveMovies()

        expect:
        movieSpecificationRepository.findAll({ root, cb ->
            cb.equal(root.join("directors").get("name"), "Lana Wachowski")
        } as PredicateSpecification<Movie>)*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
    }

    @Requires({ instance.movieSpecificationRepository != null && instance.supportsRelationshipWrites() })
    void "graph criteria match relationships of the root node"() {
        given:
        saveMovies()
        def lana = personRepository.findByName("Lana Wachowski").get()
        def keanu = personRepository.findByName("Keanu Reeves").get()
        def specs = movieSpecificationRepository

        expect: "movies with any director"
        specs.findAll({ root, cb -> GraphCriteria.related(root, "directors") } as PredicateSpecification<Movie>)*.title.sort() == ["The Matrix", "The Matrix Reloaded"]

        and: "movies without a director"
        specs.findAll({ root, cb -> cb.not(GraphCriteria.related(root, "directors")) } as PredicateSpecification<Movie>)*.title == ["Speed"]

        and: "movies directed by a given person, matched by identifier"
        specs.findAll({ root, cb -> GraphCriteria.relatedTo(root, "directors", cb.literal(lana.id)) } as PredicateSpecification<Movie>)*.title.sort() == ["The Matrix", "The Matrix Reloaded"]

        and: "movies with a role played by a given person, through the relationship entity"
        specs.count({ root, cb -> GraphCriteria.relatedTo(root, "roles", cb.literal(keanu.id)) } as PredicateSpecification<Movie>) == 3

        and: "graph criteria combine with property criteria"
        specs.findAll({ root, cb ->
            cb.and(GraphCriteria.related(root, "directors"), cb.greaterThan(root.get("released"), 2000))
        } as PredicateSpecification<Movie>)*.title == ["The Matrix Reloaded"]
    }

    @Requires({ instance.movieSpecificationRepository != null && instance.supportsRelationshipWrites() && instance.supportsConnectedCriteria() })
    void "graph criteria follow chains of relationships"() {
        given:
        saveMovies()

        expect: "movies within two hops of Speed over any relationship: those sharing an actor"
        movieSpecificationRepository.findAll({ root, cb ->
            GraphCriteria.connected(root, null, cb.literal("Speed"), 2)
        } as PredicateSpecification<Movie>)*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
    }

    @Requires({ instance.movieSpecificationRepository != null })
    void "update and delete nodes with criteria specifications"() {
        given:
        saveMovies()

        when:
        def updated = movieSpecificationRepository.updateAll({ root, query, cb ->
            query.set("votes", 10L)
            cb.greaterThan(root.get("released"), 1995)
        } as UpdateSpecification<Movie>)

        then:
        updated == 2
        movieRepository.findById("The Matrix").get().votes == 10L
        movieRepository.findById("Speed").get().votes == null

        when:
        def deleted = movieSpecificationRepository.deleteAll({ root, query, cb ->
            cb.lessThan(root.get("released"), 2000)
        } as DeleteSpecification<Movie>)

        then:
        deleted == 2
        movieRepository.findAll()*.title == ["The Matrix Reloaded"]
    }

    @Requires({ instance.supportsRelationshipFinders() })
    void "path finders match endpoints by a property and override the direction"() {
        given:
        def barbara = new Person("Barbara Liskov", 1939)
        def grace = new Person("Grace Hopper", 1906)
        grace.mentor = barbara
        def ada = new Person("Ada Lovelace", 1815)
        ada.mentor = grace
        savePerson(ada)

        expect: "MENTORED_BY points from the mentee to the mentor"
        personRepository.existsPathByMentorBetweenName("Ada Lovelace", "Barbara Liskov")
        !personRepository.existsPathByMentorBetweenName("Barbara Liskov", "Ada Lovelace")

        and: "an incoming path follows the relationships backwards"
        personRepository.hasPathByMentorBetweenName("Barbara Liskov", "Ada Lovelace")
        !personRepository.hasPathByMentorBetweenName("Ada Lovelace", "Barbara Liskov")
    }

    @Requires({ instance.supportsRelationshipFinders() })
    void "path finders take the direction as an argument"() {
        given:
        def barbara = new Person("Barbara Liskov", 1939)
        def grace = new Person("Grace Hopper", 1906)
        grace.mentor = barbara
        def ada = new Person("Ada Lovelace", 1815)
        ada.mentor = grace
        savePerson(ada)

        expect: "the argument overrides the direction of MENTORED_BY"
        personRepository.countPathsByMentorBetweenName("Ada Lovelace", "Barbara Liskov", Direction.OUTGOING) == 1
        personRepository.countPathsByMentorBetweenName("Ada Lovelace", "Barbara Liskov", Direction.INCOMING) == 0
        personRepository.countPathsByMentorBetweenName("Barbara Liskov", "Ada Lovelace", Direction.INCOMING) == 1
        personRepository.countPathsByMentorBetweenName("Barbara Liskov", "Ada Lovelace", Direction.OUTGOING) == 0
    }

    @Requires({ instance.supportsRelationshipFinders() })
    void "relationship finders end at another entity"() {
        given:
        saveMovies()

        expect:
        movieRepository.countRelationshipsByDirectors() == 2
        movieRepository.existsRelationshipByDirectorsBetweenTitleAndName("The Matrix", "Lana Wachowski")
        !movieRepository.existsRelationshipByDirectorsBetweenTitleAndName("The Matrix", "Keanu Reeves")
        !movieRepository.existsRelationshipByDirectorsBetweenTitleAndName("Speed", "Lana Wachowski")
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsPaths() })
    void "path and relationship finders take the direction as an argument"() {
        given:
        saveFriends()

        expect:
        friendRepository.existsPathByFriends("Fred", "Joe", Direction.OUTGOING)
        !friendRepository.existsPathByFriends("Joe", "Fred", Direction.OUTGOING)
        friendRepository.existsPathByFriends("Joe", "Fred", Direction.INCOMING)
        friendRepository.existsPathByFriends("Joe", "Fred", Direction.UNDIRECTED)

        and: "an undirected argument without endpoints counts each relationship once"
        friendRepository.countRelationshipsByFriends(Direction.OUTGOING) == 4
        friendRepository.countRelationshipsByFriends(Direction.INCOMING) == 4
        friendRepository.countRelationshipsByFriends(Direction.UNDIRECTED) == 4
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsPaths() })
    void "relationship finders return relationship entities"() {
        given:
        saveMovies()
        def keanu = personRepository.findByName("Keanu Reeves").get()

        when:
        def roles = movieRepository.findRelationshipsByRoles("The Matrix", keanu.id)

        then:
        roles.size() == 1
        roles[0] instanceof Role
        roles[0].id != null
        roles[0].roles == ["Neo"]
        roles[0].person.name == "Keanu Reeves"
        movieRepository.findRelationshipsByRoles("Speed", keanu.id)*.roles == [["Jack Traven"]]
        movieRepository.findRelationshipsByRoles("Speed", UUID.randomUUID()).isEmpty()
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsPaths() })
    void "path and relationship finders end at another entity"() {
        given:
        saveMovies()
        def keanu = personRepository.findByName("Keanu Reeves").get()
        def lana = personRepository.findByName("Lana Wachowski").get()

        when:
        def directed = movieRepository.findRelationshipsByDirectors("The Matrix", lana.id)

        then:
        directed.size() == 1
        directed[0].type() == "DIRECTED"
        directed[0].from() instanceof Person
        directed[0].from().name == "Lana Wachowski"
        directed[0].to() instanceof Movie
        directed[0].to().title == "The Matrix"

        when:
        def paths = movieRepository.findAllPathsToPerson("The Matrix", keanu.id)

        then: "the direct path, and the paths through the other movies Keanu acted in and Lana directed"
        paths.size() > 1
        paths[0].length() == 1
        paths[0].start() instanceof Movie
        paths[0].end() instanceof Person
        paths[0].end().name == "Keanu Reeves"

        when:
        def path = movieRepository.findShortestPathToPersonBetweenTitleAndName("Speed", "Carrie-Anne Moss")

        then: "Speed, Keanu, The Matrix, Carrie-Anne"
        path.present
        path.get().length() == 3
        path.get().nodes().collect { it instanceof Movie ? it.title : it.name } == ["Speed", "Keanu Reeves", "The Matrix", "Carrie-Anne Moss"]
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsPaths() })
    void "find the shortest path between nodes matched by a property"() {
        given:
        def barbara = new Person("Barbara Liskov", 1939)
        def grace = new Person("Grace Hopper", 1906)
        grace.mentor = barbara
        def ada = new Person("Ada Lovelace", 1815)
        ada.mentor = grace
        personRepository.save(ada)

        when:
        def path = ((PersonQueryRepository) personRepository).findShortestPathByMentorBetweenName("Ada Lovelace", "Barbara Liskov")

        then:
        path.present
        path.get().nodes()*.name == ["Ada Lovelace", "Grace Hopper", "Barbara Liskov"]
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsRelationshipReads() && instance.supportsExplicitQueries() })
    void "fetch the relationships of related nodes and collect values into a DTO"() {
        given:
        def mentor = new Person("Andrzej Wajda", 1926)
        def director = new Person("Agnieszka Holland", 1948)
        director.mentor = mentor
        def movie = new Movie("Europa Europa", "A boy survives the war", 1990)
        movie.directors = [director, new Person("Second Unit", 1960)]
        movieRepository.save(movie)
        def queries = (MovieQueryRepository) movieRepository

        when:
        def found = queries.queryByTitle("Europa Europa").get()

        then: "each director carries the mentor fetched with it"
        found.directors*.name.sort() == ["Agnieszka Holland", "Second Unit"]
        found.directors.find { it.name == "Agnieszka Holland" }.mentor.name == "Andrzej Wajda"
        found.directors.find { it.name == "Second Unit" }.mentor == null

        when:
        def names = queries.findDirectorNames("Europa Europa").get()

        then:
        names.title() == "Europa Europa"
        names.directors().sort() == ["Agnieszka Holland", "Second Unit"]

        when: "an explicit query returns related nodes in a j_ column"
        def withDirectors = queries.findWithDirectors("Europa Europa").get()

        then:
        withDirectors.directors*.name.sort() == ["Agnieszka Holland", "Second Unit"]
    }
}
