package io.micronaut.data.age

import io.micronaut.data.age.features.Ensemble
import io.micronaut.data.age.features.EnsembleRepository
import io.micronaut.data.age.features.Seat
import io.micronaut.data.age.features.Section
import io.micronaut.data.age.operations.AgeRepositoryOperations
import io.micronaut.data.age.tck.AgeArticleRepository
import io.micronaut.data.age.tck.AgeColleagueRepository
import io.micronaut.data.age.tck.AgeFriendRepository
import io.micronaut.data.age.tck.AgeMovieRepository
import io.micronaut.data.age.tck.AgePersonRepository
import io.micronaut.data.graph.tck.AbstractGraphRepositorySpec
import io.micronaut.data.graph.tck.entities.Friend
import io.micronaut.data.model.Pageable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject

import java.sql.Connection

/**
 * Runs the graph TCK against PostgreSQL with Apache AGE, started by Micronaut Test Resources.
 */
@MicronautTest(transactional = false)
class AgeGraphRepositorySpec extends AbstractGraphRepositorySpec {

    @Inject
    AgeArticleRepository articleRepository

    @Inject
    AgeMovieRepository movieRepository

    @Inject
    AgePersonRepository personRepository

    @Inject
    AgeFriendRepository friendRepository

    @Inject
    AgeColleagueRepository colleagueRepository

    @Inject
    EnsembleRepository ensembleRepository

    @Inject
    TransactionOperations<Connection> transactionOperations

    @Inject
    AgeRepositoryOperations operations

    @Override
    void cleanupGraph() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "a relationship entity without an identifier is written, read and written again"() {
        given:
        def ensemble = new Ensemble("Quartet", [new Seat(1, new Section("first violin"))])

        when:
        ensembleRepository.save(ensemble)

        then:
        chairs() == [1]

        when:
        def found = ensembleRepository.findByName("Quartet").get()

        then:
        found.seats() == [new Seat(1, new Section("first violin"))]

        when: "the same two nodes are the same relationship, as there is no identifier to tell them apart"
        ensembleRepository.update(new Ensemble("Quartet", [new Seat(2, new Section("first violin"))]))

        then:
        chairs() == [2]
    }

    private List<Object> chairs() {
        return operations.executeQuery("MATCH (:Ensemble)-[s:SEATS]->(:Section) RETURN s.chair AS chair", [:])*.get("chair").collect { it as int }
    }

    void "a page of a shortest path counts the shortest path, not every path up to the depth"() {
        given: "two paths of two hops and one of three from Fred to Joe"
        def fred = new Friend("Fred")
        def barney = new Friend("Barney")
        def wilma = new Friend("Wilma")
        def pebbles = new Friend("Pebbles")
        def joe = new Friend("Joe")
        fred.friends = [barney, wilma, pebbles]
        barney.friends = [joe]
        wilma.friends = [joe]
        pebbles.friends = [new Friend("Bamm-Bamm").tap { it.friends = [joe] }]
        friendRepository.save(fred)

        when:
        def page = friendRepository.findPathByFriends("Fred", "Joe", Pageable.from(0, 10))

        then:
        page.content.size() == 1
        page.content[0].length() == 2
        page.totalSize == 1

        when: "the page is unpaged, the count query still runs"
        page = friendRepository.findPathByFriends("Fred", "Joe", Pageable.unpaged())

        then:
        page.content.size() == 1
        page.totalSize == 1

        when:
        def none = friendRepository.findPathByFriends("Fred", "Nobody", Pageable.from(0, 10))

        then:
        none.content.empty
        none.totalSize == 0

        when:
        def allShortest = friendRepository.findAllShortestPathsByFriends("Fred", "Joe", Pageable.from(0, 1))

        then:
        allShortest.content.size() == 1
        allShortest.totalSize == 2

        when: "the query matches its path twice, so it has the limit twice"
        def bounded = friendRepository.findAllShortestPathsByFriends("Fred", "Joe", 5)

        then:
        bounded.size() == 2
    }
}
