package io.micronaut.data.graph.tck

import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.exceptions.OptimisticLockException
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
import io.micronaut.data.graph.tck.repositories.MovieRepository
import io.micronaut.data.graph.tck.repositories.PersonRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.transaction.TransactionOperations
import spock.lang.Requires
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
     * Whether the repositories run Cypher: explicit Cypher queries, fetch joins and path finders.
     *
     * @return True for Cypher databases
     */
    boolean supportsCypher() {
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
        movieRepository.save(matrix)
        movieRepository.save(reloaded)
        movieRepository.save(speed)
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

    @Requires({ instance.supportsRelationshipWrites() })
    void "find by criteria on relationships"() {
        given:
        saveMovies()

        expect:
        movieRepository.findByDirectorsName("Lana Wachowski")*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
        movieRepository.findByRolesPersonName("Keanu Reeves")*.title.sort() == ["Speed", "The Matrix", "The Matrix Reloaded"]
        movieRepository.findByRolesPersonName("Carrie-Anne Moss")*.title == ["The Matrix"]
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsCypher() })
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

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsCypher() })
    void "fetch a single relationship"() {
        given:
        def mentor = new Person("Morpheus", 1961)
        def student = new Person("Neo", 1964)
        student.mentor = mentor
        personRepository.save(student)

        expect:
        personRepository.findByMentorName("Morpheus")*.name == ["Neo"]
        personRepository.queryByName("Neo").get().mentor.name == "Morpheus"
        personRepository.findByName("Neo").get().mentor == null
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
        !supportsRelationshipWrites() || personRepository.findByName("Keanu Reeves").present
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsCypher() })
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

    @Requires({ instance.supportsCypher() })
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

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsCypher() })
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

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsCypher() })
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

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsCypher() })
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

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsCypher() })
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
    void "inserting a node with the identifier of an existing node fails rather than overwrite it"() {
        given:
        articleRepository.save(new Article("graphs", "Graphs"))
        def loaded = articleRepository.findById("graphs").get()
        loaded.title = "Graph databases"
        articleRepository.update(loaded)

        when:
        articleRepository.save(new Article("graphs", "Other"))

        then:
        thrown(EntityExistsException)
        with(articleRepository.findById("graphs").get()) {
            title == "Graph databases"
            version == 1L
        }
        articleRepository.count() == 1
    }

    @Requires({ instance.articleRepository != null })
    void "concurrent updates of the same version fail rather than lose an update"() {
        given:
        articleRepository.save(new Article("graphs", "Graphs"))
        def first = articleRepository.findById("graphs").get()
        def second = articleRepository.findById("graphs").get()

        when: "the second update runs while the first holds the node uncommitted"
        def error = concurrently({ first.title = "First"; articleRepository.update(first) },
            { second.title = "Second"; articleRepository.update(second) })

        then:
        error instanceof OptimisticLockException
        with(articleRepository.findById("graphs").get()) {
            title == "First"
            version == 1L
        }
    }

    @Requires({ instance.articleRepository != null })
    void "a delete concurrent with an update of the same version fails rather than delete the update"() {
        given:
        articleRepository.save(new Article("graphs", "Graphs"))
        def first = articleRepository.findById("graphs").get()
        def stale = articleRepository.findById("graphs").get()

        when:
        def error = concurrently({ first.title = "First"; articleRepository.update(first) }, { articleRepository.delete(stale) })

        then:
        error instanceof OptimisticLockException
        articleRepository.findById("graphs").get().title == "First"
    }

    /**
     * Runs a write in a transaction that stays open for a while after it, and another write in its own transaction
     * once the first has written.
     *
     * @return The error of the second write, or null
     */
    private Throwable concurrently(Closure<?> firstWrite, Closure<?> secondWrite) {
        def written = new CountDownLatch(1)
        def executor = Executors.newSingleThreadExecutor()
        try {
            def first = executor.submit({
                transactionOperations.executeWrite { status ->
                    firstWrite.call()
                    written.countDown()
                    // Hold the lock of the node while the second write reads it
                    Thread.sleep(1000)
                    null
                }
            } as Callable)
            assert written.await(30, TimeUnit.SECONDS)
            Throwable error = null
            try {
                transactionOperations.executeWrite { status ->
                    secondWrite.call()
                    null
                }
            } catch (Throwable e) {
                error = e
            }
            first.get(30, TimeUnit.SECONDS)
            return error
        } finally {
            executor.shutdownNow()
        }
    }
}
