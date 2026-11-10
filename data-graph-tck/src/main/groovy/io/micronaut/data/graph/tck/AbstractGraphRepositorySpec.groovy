package io.micronaut.data.graph.tck

import io.micronaut.data.annotation.Join
import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.graph.tck.entities.Article
import io.micronaut.data.graph.tck.entities.Colleague
import io.micronaut.data.graph.tck.entities.Mentorship
import io.micronaut.data.graph.tck.entities.Collaboration
import io.micronaut.data.graph.tck.entities.Friend
import io.micronaut.data.graph.tck.entities.Milestone
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.graph.tck.entities.Step
import io.micronaut.data.graph.tck.repositories.ArticleRepository
import io.micronaut.data.graph.tck.repositories.ColleagueRepository
import io.micronaut.data.graph.tck.repositories.FriendRepository
import io.micronaut.data.graph.tck.repositories.MilestoneRepository
import io.micronaut.data.graph.tck.repositories.MovieQueryRepository
import io.micronaut.data.graph.tck.repositories.MovieRepository
import io.micronaut.data.graph.tck.repositories.MovieSpecificationRepository
import io.micronaut.data.graph.tck.repositories.PersonQueryRepository
import io.micronaut.data.graph.tck.repositories.PersonRepository
import io.micronaut.data.graph.tck.repositories.StepRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
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
     * The repository of an immutable entity whose identifier the entity events populate, or null if the
     * implementation does not write relationships.
     *
     * @return The repository
     */
    StepRepository getStepRepository() {
        null
    }

    /**
     * The repository of an immutable entity with an assigned identifier and a {@code @Version} property, or null if
     * the implementation does not write relationships.
     *
     * @return The repository
     */
    MilestoneRepository getMilestoneRepository() {
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
     * Whether a batch that holds an instance more than once writes it once. Oracle repositories hand a batch to
     * {@code persistAll} of Micronaut Data JDBC, which inserts it for each time it appears.
     *
     * @return True if an instance repeated in a batch is written once
     */
    boolean supportsRepeatedInstancesInBatches() {
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

    @Requires({ instance.supportsRelationshipWrites() })
    void "find by a relationship entity compares the relationship, and by its target node compares the node"() {
        given:
        def keanu = new Person("Keanu Reeves", 1964)
        def carrie = new Person("Carrie-Anne Moss", 1967)
        def matrix = new Movie("The Matrix", "Welcome to the Real World", 1999)
        matrix.roles = [new Role(["Neo"], keanu), new Role(["Trinity"], carrie)]
        def speed = new Movie("Speed", "Get ready for rush hour", 1994)
        speed.roles = [new Role(["Jack Traven"], keanu)]
        matrix = movieRepository.save(matrix)
        speed = movieRepository.save(speed)

        expect: "only the movie that holds the role, not every movie of the same person"
        speed.roles[0].id != null
        movieRepository.findByRoles(speed.roles[0])*.title == ["Speed"]
        movieRepository.findByRoles(matrix.roles[0])*.title == ["The Matrix"]

        and:
        movieRepository.findByRolesPerson(keanu)*.title.sort() == ["Speed", "The Matrix"]
        movieRepository.findByRolesPerson(carrie)*.title == ["The Matrix"]
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
        def saved = colleagueRepository.save(ada).colleagues[0]

        when: "the relationship is saved again from its other end"
        def again = new Collaboration("cobol", new Colleague("Ada"))
        again.id = saved.id
        grace.colleagues = [again]
        def resaved = colleagueRepository.save(grace).colleagues[0]

        then: "the relationship is matched in either direction, so its properties are updated rather than a second one created"
        resaved.id == saved.id
        colleagueRepository.countCollaborations() == 1
        colleagueRepository.findByColleaguesProject("cobol")*.name.sort() == ["Ada", "Grace"]
        colleagueRepository.findByColleaguesProject("compiler").isEmpty()
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsExplicitQueries() })
    void "new undirected relationships between the same nodes stay separate"() {
        given:
        def ada = new Colleague("Ada")
        def grace = new Colleague("Grace")
        ada.colleagues = [new Collaboration("compiler", grace), new Collaboration("cobol", grace)]

        when:
        def saved = colleagueRepository.save(ada).colleagues

        then:
        saved*.id.every { it != null }
        saved*.id.toSet().size() == 2
        colleagueRepository.countCollaborations() == 2
        colleagueRepository.findByColleaguesProject("compiler")*.name.sort() == ["Ada", "Grace"]
        colleagueRepository.findByColleaguesProject("cobol")*.name.sort() == ["Ada", "Grace"]

        when: "another one that was not saved is added from the other end"
        grace.colleagues = [new Collaboration("flow-matic", new Colleague("Ada"))]
        colleagueRepository.save(grace)

        then:
        colleagueRepository.countCollaborations() == 3
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsExplicitQueries() })
    void "replacing a single valued relationship entity leaves one relationship"() {
        given:
        def ada = new Colleague("Ada")
        ada.mentor = new Mentorship("first", new Colleague("Grace"))
        ada = colleagueRepository.save(ada)
        def saved = ada.mentor

        when: "it is saved again"
        def again = new Mentorship("second", new Colleague("Grace"))
        again.id = saved.id
        ada.mentor = again
        ada = colleagueRepository.update(ada)

        then:
        ada.mentor.id == saved.id
        colleagueRepository.findMentorships() == ["second"]

        when: "it is replaced by one that was not saved"
        ada.mentor = new Mentorship("third", new Colleague("Grace"))
        ada = colleagueRepository.update(ada)

        then:
        ada.mentor.id != saved.id
        colleagueRepository.findMentorships() == ["third"]

        when: "it moves to another node"
        ada.mentor = new Mentorship("fourth", new Colleague("Hopper"))
        colleagueRepository.update(ada)

        then:
        colleagueRepository.findMentorships() == ["fourth"]

        when: "one that was not saved is saved from the other end"
        def hopper = new Colleague("Hopper")
        hopper.mentor = new Mentorship("fifth", new Colleague("Ada"))
        colleagueRepository.update(hopper)

        then:
        colleagueRepository.findMentorships() == ["fifth"]
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
        again.each { it.title = it.title.toUpperCase() }
        again[1] = stale
        articleRepository.updateAll(again)

        then: "no node of the batch is updated"
        thrown(OptimisticLockException)
        articleRepository.findAll().sort { it.slug }.collect { [it.title, it.version] } == [["a", 1L], ["b", 1L], ["c", 1L]]

        when: "one node of a batch of new nodes has the identifier of an existing node"
        articleRepository.saveAll([new Article("d", "D"), new Article("b", "Other"), new Article("e", "E")])

        then: "no node of the batch is inserted and the existing node is kept"
        thrown(EntityExistsException)
        articleRepository.count() == 3
        with(articleRepository.findById("b").get()) {
            title == "b"
            version == 1L
        }
    }

    @Requires({ instance.supportsRepeatedInstancesInBatches() })
    void "saveAll writes an instance passed twice once"() {
        given:
        def ada = new Person("Ada Lovelace", 1815)

        when:
        def saved = personRepository.saveAll([ada, new Person("Grace Hopper", 1906), ada])

        then:
        saved.size() == 3
        saved[0].id != null
        saved[0].id == saved[2].id
        personRepository.count() == 2
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsExplicitQueries() })
    void "saveAll and updateAll write relationship entities by their identifiers"() {
        given:
        def ada = new Colleague("Ada")
        def grace = new Colleague("Grace")
        ada.colleagues = [new Collaboration("compiler", grace), new Collaboration("cobol", grace)]

        when: "two new relationships link the same nodes"
        def saved = colleagueRepository.saveAll([ada, new Colleague("Lin")]).toList()
        def ids = saved[0].colleagues*.id

        then:
        ids.every { it != null }
        ids.toSet().size() == 2
        colleagueRepository.countCollaborations() == 2

        when: "one of them is written again with its identifier, and a new one is added"
        def again = new Collaboration("cobol-60", grace)
        again.id = ids[1]
        saved[0].colleagues = [saved[0].colleagues[0], again, new Collaboration("flow-matic", grace)]
        def updated = colleagueRepository.updateAll(saved).toList()

        then: "the saved one is updated and the other is created"
        updated[0].colleagues[1].id == ids[1]
        colleagueRepository.countCollaborations() == 3
        colleagueRepository.findByColleaguesProject("cobol").isEmpty()
        colleagueRepository.findByColleaguesProject("cobol-60")*.name.sort() == ["Ada", "Grace"]
        colleagueRepository.findByColleaguesProject("flow-matic")*.name.sort() == ["Ada", "Grace"]
    }

    @Requires({ instance.stepRepository != null })
    void "saveAll links records in a batch to the nodes of the batch"() {
        given: "records that the entity events replace, which refer to the instances passed to saveAll"
        def last = new Step(null, "last", null)
        def middle = new Step(null, "middle", last)
        def first = new Step(null, "first", middle)
        def batch = [first: first, middle: middle, last: last]

        when:
        def saved = stepRepository.saveAll(order.collect { batch[it] }).collectEntries { [it.name(), it] }

        then: "no node is created twice"
        stepRepository.count() == 3
        saved*.value*.id().toSet().size() == 3
        !saved*.value*.id().contains(null)

        and: "the returned references are complete, wherever they are in the batch"
        saved.first.next().id() == saved.middle.id()
        saved.first.next().next().id() == saved.last.id()
        saved.middle.next().id() == saved.last.id()

        where:
        order << [["first", "middle", "last"], ["last", "middle", "first"], ["middle", "first", "last"]]
    }

    @Requires({ instance.milestoneRepository != null })
    void "saveAll and updateAll link records with assigned identifiers and versions"() {
        given:
        def last = new Milestone("last", null, null)
        def middle = new Milestone("middle", null, last)
        def first = new Milestone("first", null, middle)

        when: "the related records are also in the batch, so none is inserted twice"
        def saved = milestoneRepository.saveAll([first, middle, last])

        then:
        milestoneRepository.count() == 3
        saved*.version() == [0L, 0L, 0L]
        saved[0].next().next().version() == 0L

        when: "the results are saved again, so none is updated twice"
        def updated = milestoneRepository.updateAll(saved)

        then:
        updated*.version() == [1L, 1L, 1L]
        updated[0].next().version() == 1L
        updated[0].next().next().version() == 1L
        milestoneRepository.findById("first").get().version() == 1L
    }

    @Requires({ instance.supportsRelationshipWrites() })
    void "saveAll links mutable entities to the nodes of the batch"() {
        given:
        def last = new Person("Ada Lovelace", 1815)
        def middle = new Person("Grace Hopper", 1906)
        middle.mentor = last
        def first = new Person("Katherine Johnson", 1918)
        first.mentor = middle

        when:
        def saved = personRepository.saveAll([first, middle, last])

        then:
        personRepository.count() == 3
        saved[0].mentor.is(saved[1])
        saved[0].mentor.mentor.id == saved[2].id
        saved[2].id != null
    }

    @Requires({ instance.articleRepository != null })
    void "a batch of mutable entities that repeats an identifier conflicts as sequential saves do"() {
        when: "two new instances have one identifier"
        transactionOperations.executeWrite { articleRepository.saveAll([new Article("a", "First"), new Article("a", "Second"), new Article("b", "B")]) }

        then:
        thrown(EntityExistsException)
        articleRepository.count() == 0

        when: "two copies of one node have the version they were read with"
        articleRepository.saveAll([new Article("a", "A"), new Article("b", "B")])
        def copy = articleRepository.findById("a").get()
        def other = articleRepository.findById("a").get()
        copy.title = "First"
        other.title = "Second"
        def third = articleRepository.findById("b").get()
        transactionOperations.executeWrite { articleRepository.updateAll([copy, other, third]) }

        then:
        thrown(OptimisticLockException)
        with(articleRepository.findById("a").get()) {
            title == "A"
            version == 0L
        }
    }

    @Requires({ instance.milestoneRepository != null })
    void "a batch of records that repeats an identifier conflicts as sequential saves do"() {
        when: "two new instances have one identifier"
        transactionOperations.executeWrite { milestoneRepository.saveAll([new Milestone("a", null, null), new Milestone("a", null, null), new Milestone("b", null, null)]) }

        then:
        thrown(EntityExistsException)
        milestoneRepository.count() == 0

        when: "two copies of one node have the version they were read with"
        milestoneRepository.saveAll([new Milestone("a", null, null), new Milestone("b", null, null)])
        def copy = milestoneRepository.findById("a").get()
        transactionOperations.executeWrite { milestoneRepository.updateAll([copy, new Milestone("a", copy.version(), null)]) }

        then:
        thrown(OptimisticLockException)
        milestoneRepository.findById("a").get().version() == 0L
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

        when: "a director is linked to the movie by a second, parallel relationship"
        queries.addDirector("Agnieszka Holland", "Europa Europa")
        found = queries.queryByTitle("Europa Europa").get()

        then: "the director is fetched once"
        found.directors*.name.sort() == ["Agnieszka Holland", "Second Unit"]
        found.directors.find { it.name == "Agnieszka Holland" }.mentor.name == "Andrzej Wajda"
    }

    @Requires({ instance.supportsRelationshipWrites() && instance.supportsRelationshipReads() && instance.supportsExplicitQueries() })
    void "nested inner fetch joins exclude the nodes without the relationship"() {
        given:
        def director = new Person("Agnieszka Holland", 1948)
        director.mentor = new Person("Andrzej Wajda", 1926)
        def mentored = new Movie("Europa Europa", "A boy survives the war", 1990)
        mentored.directors = [director, new Person("Second Unit", 1960)]
        def unmentored = new Movie("Speed", "Get ready for rush hour", 1994)
        unmentored.directors = [new Person("Jan de Bont", 1943)]
        movieRepository.save(mentored)
        movieRepository.save(unmentored)
        movieRepository.save(new Movie("Sans Director", "Nobody directed this", 1995))
        def queries = (MovieQueryRepository) movieRepository

        when: "both joins are inner"
        def found = queries.queryByReleasedGreaterThan(1900)

        then:
        found*.title == ["Europa Europa"]
        found[0].directors*.name == ["Agnieszka Holland"]
        found[0].directors[0].mentor.name == "Andrzej Wajda"

        when: "only the first join is inner"
        found = queries.listByReleasedGreaterThan(1900)

        then:
        found*.title.sort() == ["Europa Europa", "Speed"]
        found.find { it.title == "Europa Europa" }.directors*.name.sort() == ["Agnieszka Holland", "Second Unit"]
        found.find { it.title == "Speed" }.directors[0].mentor == null
    }

    @Requires({ instance.movieSpecificationRepository != null && instance.supportsRelationshipWrites() && instance.supportsRelationshipReads() && instance.supportsExplicitQueries() })
    void "nested inner fetch joins of a criteria specification exclude the nodes without the relationship"() {
        given:
        def director = new Person("Agnieszka Holland", 1948)
        director.mentor = new Person("Andrzej Wajda", 1926)
        def mentored = new Movie("Europa Europa", "A boy survives the war", 1990)
        mentored.directors = [director]
        movieRepository.save(mentored)
        movieRepository.save(new Movie("Sans Director", "Nobody directed this", 1995))

        when:
        def found = movieSpecificationRepository.findAll({ root, cb ->
            root.join("directors", Join.Type.INNER_FETCH).join("mentor", Join.Type.INNER_FETCH)
            cb.greaterThan(root.get("released"), 1900)
        } as PredicateSpecification<Movie>)

        then:
        found*.title == ["Europa Europa"]
        found[0].directors[0].mentor.name == "Andrzej Wajda"
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
