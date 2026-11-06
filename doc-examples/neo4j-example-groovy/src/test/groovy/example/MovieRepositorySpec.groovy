package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class MovieRepositorySpec extends AbstractNeo4jSpec {

    @Inject
    MovieRepository movieRepository

    @Inject
    PersonRepository personRepository

    @Inject
    MovieSpecificationRepository movieSpecificationRepository

    void setup() {
        // tag::save[]
        def keanu = new Person(name: "Keanu Reeves", born: 1964)
        def carrie = new Person(name: "Carrie-Anne Moss", born: 1967)
        def lana = new Person(name: "Lana Wachowski", born: 1965)

        def matrix = new Movie(title: "The Matrix", description: "Welcome to the Real World", released: 1999)
        matrix.roles = [new Role(roles: ["Neo"], person: keanu), new Role(roles: ["Trinity"], person: carrie)]
        matrix.directors = [lana]

        movieRepository.save(matrix) // <1>
        // end::save[]
        movieRepository.save(new Movie(title: "The Matrix Reloaded", description: "Free your mind", released: 2003,
            roles: [new Role(roles: ["Neo"], person: keanu)], directors: [lana]))
        movieRepository.save(new Movie(title: "Speed", description: "Get ready for rush hour", released: 1994,
            roles: [new Role(roles: ["Jack Traven"], person: keanu)]))
    }

    void cleanup() {
        movieRepository.deleteAll()
        personRepository.deleteAll()
    }

    void "find movies"() {
        when:
        // tag::find[]
        def recent = movieRepository.findByReleasedGreaterThanOrderByReleased(1995)
        def older = movieRepository.countByReleasedLessThan(2000)
        def page = movieRepository.findByReleasedGreaterThan(1990, Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))))
        // end::find[]

        then:
        recent*.title == ["The Matrix", "The Matrix Reloaded"]
        older == 2
        page.totalSize == 3
        page.content*.title == ["Speed", "The Matrix"]
        movieRepository.findByTitleContainsIgnoreCase("matrix").size() == 2
    }

    void "traverse relationships"() {
        when:
        // tag::traverse[]
        def directedByLana = movieRepository.findByDirectorsName("Lana Wachowski")
        def withKeanu = movieRepository.findByRolesPersonName("Keanu Reeves")
        // end::traverse[]

        then:
        directedByLana.size() == 2
        withKeanu.size() == 3
    }

    void "fetch relationships"() {
        when:
        // tag::fetch[]
        def matrix = movieRepository.findByTitle("The Matrix").get()
        def neo = matrix.roles.find { it.person.name == "Keanu Reeves" }
        // end::fetch[]

        then:
        neo.roles == ["Neo"]
        matrix.directors*.name == ["Lana Wachowski"]
    }

    void "fetch nested relationships"() {
        given:
        def lilly = personRepository.save(new Person(name: "Lilly Wachowski", born: 1967, follows: [new Person(name: "Lana Wachowski", born: 1965)]))
        movieRepository.save(new Movie(title: "Bound", description: "In the end, it's all about the money", released: 1996, directors: [lilly]))

        when:
        // tag::nestedFetch[]
        def fetched = movieRepository.findByReleased(1996)[0]
        def followed = fetched.directors[0].follows[0] // <1>
        // end::nestedFetch[]

        then:
        followed.name == "Lana Wachowski"
    }

    void "paths to people"() {
        given:
        def keanu = personRepository.findByName("Keanu Reeves").get()
        def lana = personRepository.findByName("Lana Wachowski").get()

        when:
        // tag::crossType[]
        def directed = movieRepository.findRelationshipsByDirectors("The Matrix", lana.id)[0]
        def director = directed.from().name // <1>

        def path = movieRepository.findShortestPathToPersonBetweenTitleAndName("Speed", "Carrie-Anne Moss").get()
        def end = path.end() // <2>
        // end::crossType[]

        then:
        director == "Lana Wachowski"
        directed.to().title == "The Matrix"
        path.length() == 3
        end instanceof Person
        end.name == "Carrie-Anne Moss"
        path.start() instanceof Movie
        movieRepository.findAllPathsToPerson("The Matrix", keanu.id)[0].length() == 1
    }

    void "relationship entities"() {
        given:
        def keanu = personRepository.findByName("Keanu Reeves").get()

        when:
        // tag::relationshipEntities[]
        def role = movieRepository.findRelationshipsByRoles("The Matrix", keanu.id)[0]
        def characters = role.roles // <1>
        def actor = role.person // <2>
        // end::relationshipEntities[]

        then:
        characters == ["Neo"]
        actor.name == "Keanu Reeves"
    }

    void "projections and explicit queries"() {
        when:
        movieRepository.vote("Speed")

        then:
        movieRepository.findTitleByReleasedGreaterThanOrderByTitle(1990) == ["Speed", "The Matrix", "The Matrix Reloaded"]
        movieRepository.findByReleasedBetween(1990, 2000).size() == 2
        movieRepository.findMoviesActedInBy("Keanu Reeves")*.title == ["Speed", "The Matrix", "The Matrix Reloaded"]
    }

    void "specifications"() {
        when:
        // tag::useSpecifications[]
        def recent = movieSpecificationRepository.findAll(MovieSpecifications.releasedAfter(1995)) // <1>
        def byLana = movieSpecificationRepository.count(MovieSpecifications.directedBy("Lana Wachowski")) // <2>
        // end::useSpecifications[]

        then:
        recent*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
        byLana == 2
    }

    void "graph criteria"() {
        when:
        // tag::useGraphCriteria[]
        def withCast = movieSpecificationRepository.count(MovieSpecifications.withRoles()) // <1>
        def nearSpeed = movieSpecificationRepository.findAll(MovieSpecifications.connectedTo("Speed", 2)) // <2>
        // end::useGraphCriteria[]

        then:
        withCast == 3
        nearSpeed*.title.sort() == ["The Matrix", "The Matrix Reloaded"]
    }
}
