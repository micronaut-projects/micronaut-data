package example

import io.micronaut.data.graph.annotation.Direction
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class PersonRepositorySpec extends AbstractNeo4jSpec {

    @Inject
    PersonRepository personRepository

    Person fred
    Person joe

    void setup() {
        fred = new Person(name: "Fred", born: 1970)
        def barney = new Person(name: "Barney", born: 1971)
        def wilma = new Person(name: "Wilma", born: 1972)
        joe = new Person(name: "Joe", born: 1973)
        fred.follows = [barney, wilma]
        barney.follows = [joe]
        wilma.follows = [joe]
        personRepository.save(fred)
    }

    void cleanup() {
        personRepository.deleteAll()
    }

    void "find paths"() {
        when:
        // tag::shortestPath[]
        def path = personRepository.findShortestPath(fred, joe).get()
        path.segments().each { segment -> // <1>
            println "${segment.start().name} -> ${segment.end().name}"
        }
        // end::shortestPath[]

        then:
        path.length() == 2
        path.start().name == "Fred"
        path.end().name == "Joe"
        !personRepository.findShortestPathByFollows(fred.id, joe.id, 1).present
        personRepository.findAllPathsByFollows(fred, joe).size() == 2
        personRepository.existsPathByFollows(fred, joe)
        !personRepository.existsPathByFollows(joe, fred)
    }

    void "find relationships"() {
        given:
        def barney = personRepository.findByName("Barney").get()

        expect:
        personRepository.findRelationships(fred, barney)*.type() == ["FOLLOWS"]
        personRepository.countRelationshipsByFollows() == 4
    }

    void "dynamic labels"() {
        when:
        // tag::labels[]
        def keanu = new Person(name: "Keanu Reeves", born: 1964)
        keanu.labels << "Actor" // <1>
        personRepository.save(keanu)

        def labels = personRepository.findByName("Keanu Reeves").get().labels // <2>
        // end::labels[]

        then:
        labels == ["Actor"] as Set
    }

    void "path direction"() {
        when:
        // tag::pathDirection[]
        def follows = personRepository.hasPathByFollows(fred, joe, Direction.OUTGOING)
        def followedBy = personRepository.hasPathByFollows(joe, fred, Direction.INCOMING)
        // end::pathDirection[]

        then:
        follows
        followedBy
        !personRepository.hasPathByFollows(joe, fred, Direction.OUTGOING)
        personRepository.hasPathByFollows(joe, fred, Direction.UNDIRECTED)

    void "link a node without saving it"() {
        when:
        // tag::cascade[]
        def keanu = personRepository.save(new Person(name: "Keanu Reeves", born: 1964)) // <1>
        def carrie = new Person(name: "Carrie-Anne Moss", born: 1967)
        carrie.knows = [keanu] // <2>
        personRepository.save(carrie)
        // end::cascade[]

        then:
        personRepository.findByKnowsName("Keanu Reeves")*.name == ["Carrie-Anne Moss"]
        personRepository.findByName("Keanu Reeves").get().born == 1964
    }

    void "path options"() {
        expect:
        personRepository.findShortestPathByFollowsBetweenName("Fred", "Joe", 3).get().end().name == "Joe"
        personRepository.existsPathByFollowsBetweenName("Joe", "Fred")
    }
}
