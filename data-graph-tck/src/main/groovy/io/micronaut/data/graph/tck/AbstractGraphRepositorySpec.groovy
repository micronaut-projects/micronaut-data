package io.micronaut.data.graph.tck

import io.micronaut.data.graph.tck.entities.Friend
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.graph.tck.repositories.FriendRepository
import io.micronaut.data.graph.tck.repositories.MovieRepository
import io.micronaut.data.graph.tck.repositories.PersonRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.transaction.TransactionOperations
import spock.lang.Specification

/**
 * Behaviour every graph repository implementation supports.
 */
abstract class AbstractGraphRepositorySpec extends Specification {

    abstract MovieRepository getMovieRepository()

    abstract PersonRepository getPersonRepository()

    abstract FriendRepository getFriendRepository()

    abstract TransactionOperations<?> getTransactionOperations()

    /**
     * Removes every node the specs create.
     */
    abstract void cleanupGraph()

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

    void "find by criteria on relationships"() {
        given:
        saveMovies()

        expect:
        movieRepository.findByDirectorsName("Lana Wachowski")*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
        movieRepository.findByRolesPersonName("Keanu Reeves")*.title.sort() == ["Speed", "The Matrix", "The Matrix Reloaded"]
        movieRepository.findByRolesPersonName("Carrie-Anne Moss")*.title == ["The Matrix"]
    }

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
        personRepository.findByName("Keanu Reeves").present
    }

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

    void "delete an entity removes its relationships"() {
        given:
        saveMovies()

        when:
        movieRepository.delete(movieRepository.findById("Speed").get())

        then:
        !movieRepository.existsById("Speed")
        movieRepository.findByRolesPersonName("Keanu Reeves").size() == 2
    }

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

        when:
        def page = movieRepository.findByReleasedGreaterThan(1990, Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))))

        then:
        page.content*.title == ["Speed", "The Matrix"]
        page.totalSize == 3
        page.totalPages == 2

        when:
        def second = movieRepository.findByReleasedGreaterThan(1990, page.nextPageable())

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
    }

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
}
