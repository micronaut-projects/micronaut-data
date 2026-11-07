package io.micronaut.data.neo4j

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.NonUniqueResultException
import io.micronaut.data.graph.runtime.GraphNode
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.neo4j.features.Employee
import io.micronaut.data.neo4j.features.EmployeeRepository
import io.micronaut.data.neo4j.features.Note
import io.micronaut.data.neo4j.features.NoteRepository
import io.micronaut.data.neo4j.features.Reading
import io.micronaut.data.neo4j.features.ReadingRepository
import io.micronaut.data.neo4j.features.ReactiveMovieRepository
import io.micronaut.data.neo4j.features.Player
import io.micronaut.data.neo4j.features.Station
import io.micronaut.data.neo4j.features.StationRepository
import io.micronaut.data.neo4j.features.Tag
import io.micronaut.data.neo4j.features.Member
import io.micronaut.data.neo4j.features.MemberRepository
import io.micronaut.data.neo4j.features.TagRepository
import io.micronaut.data.neo4j.features.Team
import io.micronaut.data.neo4j.features.TeamRepository
import io.micronaut.data.neo4j.features.Topic
import io.micronaut.data.neo4j.features.TopicRepository
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.data.neo4j.tck.Neo4jMovieRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class Neo4jFeaturesSpec extends Specification implements TestPropertyProvider {

    @Inject
    TagRepository tagRepository

    @Inject
    TopicRepository topicRepository

    @Inject
    TeamRepository teamRepository

    @Inject
    StationRepository stationRepository

    @Inject
    MemberRepository memberRepository

    @Inject
    EmployeeRepository employeeRepository

    @Inject
    Neo4jMovieRepository movieRepository

    @Inject
    ReadingRepository readingRepository

    @Inject
    NoteRepository noteRepository

    @Inject
    ReactiveMovieRepository reactiveMovieRepository

    @Inject
    Neo4jRepositoryOperations operations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    void setup() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "database generated numeric identifiers"() {
        when:
        def tag = tagRepository.save(new Tag("graph"))

        then:
        tag.id != null
        tagRepository.findById(tag.id).get().name == "graph"
        tagRepository.findByName("graph").get().id == tag.id

        when:
        tagRepository.deleteById(tag.id)

        then:
        tagRepository.count() == 0
    }

    void "a dynamic sort follows the ordering of the query and precedes its limit"() {
        given:
        def first = tagRepository.save(new Tag("a"))
        def second = tagRepository.save(new Tag("a"))
        tagRepository.save(new Tag("b"))
        tagRepository.save(new Tag("c"))

        expect: "the declared ordering takes precedence over the sort"
        tagRepository.findByNameStartsWithOrderByNameDesc("a", Sort.of(Sort.Order.asc("id")))*.id == [second.id, first.id].sort()
        tagRepository.findByNameStartsWithOrderByNameDesc("a", Sort.of(Sort.Order.desc("id")))*.id == [second.id, first.id].sort().reverse()

        and: "the sort is applied before the limit of the query"
        tagRepository.findFirst2ByNameStartsWith("", Sort.of(Sort.Order.desc("name")))*.name == ["c", "b"]
    }

    void "a sort or a page that an explicit query cannot take is rejected"() {
        when: "the query does not match the root node as n"
        tagRepository.allTags(Sort.of(Sort.Order.asc("name")))

        then:
        def sorted = thrown(DataAccessException)
        sorted.message.contains("does not match the root node")

        when: "the query limits itself"
        tagRepository.firstTag(Pageable.from(0, 1))

        then:
        def paged = thrown(DataAccessException)
        paged.message.contains("declares SKIP or LIMIT itself")
    }

    void "a dynamic sort or page finds the clauses of an explicit query outside literals and comments and in any case"() {
        given:
        ["c", "a", "b"].each { tagRepository.save(new Tag(it)) }
        def byName = Sort.of(Sort.Order.asc("name"))

        expect: "the order goes after the query, not inside the literal"
        tagRepository.withLimitInLiteral(byName)*.name == ["a", "b", "c"]

        and: "the order goes before a lowercase limit"
        tagRepository.lowerCaseLimit(byName)*.name == ["a", "b"]

        and: "a comment that ends the query does not swallow the order"
        tagRepository.withTrailingComment(byName)*.name == ["a", "b", "c"]

        when: "a lowercase limit is still the limit of the query"
        tagRepository.lowerCaseLimitPaged(Pageable.from(0, 1))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("declares SKIP or LIMIT itself")
    }

    void "a cursored page is rejected instead of being answered with offset paging"() {
        given:
        tagRepository.save(new Tag("a"))

        when:
        tagRepository.findByNameStartsWith("a", Pageable.from(0, 1))

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("do not support cursored pagination")

        when: "a cursored pageable reaches a method that returns a page"
        tagRepository.findAll(CursoredPageable.from(1, Sort.of(Sort.Order.asc("name"))))

        then:
        e = thrown(UnsupportedOperationException)
        e.message.contains("do not support cursored pagination")
    }

    void "a case-insensitive LIKE folds the case of non-ASCII letters"() {
        given:
        ["Éclair", "Eclair"].each { tagRepository.save(new Tag(it)) }

        expect:
        tagRepository.findByNameIlike("é%")*.name == ["Éclair"]
    }

    void "an Object[] row holds the columns of the query, not a node or its properties"() {
        given:
        tagRepository.save(new Tag("a"))

        when:
        def rows = tagRepository.tagRows()

        then:
        rows.size() == 1
        rows[0].length == 1
        rows[0][0] instanceof GraphNode

        when: "the query returns several columns"
        def columns = tagRepository.tagColumns()

        then:
        columns.size() == 1
        columns[0] as List == ["a", 1L]
    }

    void "a query with more than one result is not a single result"() {
        given:
        tagRepository.save(new Tag("dup"))
        tagRepository.save(new Tag("dup"))

        when:
        tagRepository.findByName("dup")

        then:
        thrown(NonUniqueResultException)
    }

    void "a DTO reads the columns it selects under another name"() {
        given:
        tagRepository.save(new Tag("graph"))

        expect:
        tagRepository.findAllByNameStartsWith("gr")*.label == ["graph"]
    }

    void "database generated element identifiers on immutable records"() {
        when:
        def saved = topicRepository.save(new Topic(null, "Databases", [new Tag("graph"), new Tag("nosql")]))

        then:
        saved.id() != null
        saved.tags().every { it.id != null }

        when:
        def found = topicRepository.findByTitle("Databases").get()

        then:
        found.id() == saved.id()
        found.tags()*.name.sort() == ["graph", "nosql"]
    }

    void "immutable records that reference each other"() {
        given: "a team whose player refers back to the team instance being saved"
        def players = []
        def team = new Team(null, "Wanderers", players)
        players << new Player(null, "Ada", team)

        when:
        def saved = teamRepository.save(team)

        then:
        saved.id() != null
        saved.players()*.id().every { it != null }
        saved.players()[0].team().id() == saved.id()

        and: "the player is linked to the one team node"
        teamRepository.findByName("Wanderers").get().players()*.name() == ["Ada"]
        operations.executeQuery("MATCH (p:Player)-[:PLAYS_FOR]->(t:Team) RETURN count(t)", [:], Long) == [1L]
        teamRepository.count() == 1
    }

    void "immutable records that reference each other are batched"() {
        given: "two teams whose players refer back to the team instances being saved"
        def teams = ["Wanderers", "Rovers"].collect { name ->
            def players = []
            def team = new Team(null, name, players)
            players << new Player(null, "Player of $name".toString(), team)
            team
        }

        when:
        def saved = teamRepository.saveAll(teams)

        then:
        saved*.id().every { it != null }
        saved.collectMany { it.players() }*.id().every { it != null }
        operations.executeQuery("MATCH (p:Player)-[:PLAYS_FOR]->(t:Team) RETURN count(t)", [:], Long) == [2L]
        teamRepository.count() == 2
    }

    void "path finders match endpoints by a database generated identifier"() {
        given:
        def saved = stationRepository.save(new Station(null, "A", [new Station(null, "B", [new Station(null, "C", [])])]))
        def a = saved.id()
        def c = saved.links()[0].links()[0].id()

        expect:
        stationRepository.existsPathByLinksBetweenId(a, c)
        !stationRepository.existsPathByLinksBetweenId(c, a)
        stationRepository.findShortestPathByLinksBetweenId(a, c).get().nodes()*.name() == ["A", "B", "C"]
    }

    void "reactive repositories"() {
        when:
        reactiveMovieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994)).block()
        reactiveMovieRepository.save(new Movie("The Matrix", "Welcome to the Real World", 1999)).block()

        then:
        reactiveMovieRepository.findByReleasedGreaterThan(1995).collectList().block()*.title == ["The Matrix"]
        reactiveMovieRepository.count().block() == 2
    }

    void "run explicit Cypher through the operations"() {
        given:
        tagRepository.save(new Tag("graph"))

        expect:
        operations.executeQuery("MATCH (t:Tag) RETURN t", [:], Tag)*.name == ["graph"]
        operations.executeQuery("MATCH (t:Tag) RETURN t.name AS name", [:])*.get("name") == ["graph"]
        operations.executeQuery("MATCH (t:Tag {name: \$name}) RETURN count(t)", [name: "graph"], Long) == [1L]
    }

    void "a record in a batch links to a record later in the same batch by its generated identifier"() {
        given:
        def mentor = new Member(null, "Grace", null)
        def mentee = new Member(null, "Ada", mentor)

        when:
        def saved = memberRepository.saveAll([mentee, mentor])

        then:
        saved*.id().every { it != null }
        saved[0].mentor().id() == saved[1].id()
        memberRepository.count() == 2
        operations.executeQuery("MATCH (:Member {name: 'Ada'})-[:MENTORED_BY]->(m:Member {name: 'Grace'}) RETURN count(m)", [:], Long) == [1L]
    }

    void "saveAll writes an instance passed twice once"() {
        given:
        def tag = new Tag("graph")

        when:
        def saved = tagRepository.saveAll([tag, new Tag("nosql"), tag])

        then:
        saved.size() == 3
        saved[0].id != null
        saved[0].id == saved[2].id
        tagRepository.count() == 2
    }

    void "saveAll creates nodes with database generated identifiers in one batch"() {
        when:
        def tags = tagRepository.saveAll([new Tag("graph"), new Tag("nosql"), new Tag("cypher")])

        then: "each instance gets the identifier of its own node"
        tags*.id.every { it != null }
        tags*.id.unique().size() == 3
        tags.collect { tagRepository.findById(it.id).get().name } == ["graph", "nosql", "cypher"]
    }

    void "numeric identifiers are those the queries match on"() {
        when:
        def tag = tagRepository.save(new Tag("graph"))

        then:
        operations.executeQuery("MATCH (t:Tag) RETURN id(t)", [:], Long) == [tag.id]
        tagRepository.findByName("graph").get().id == tag.id
    }

    void "sort by a database generated identifier"() {
        given:
        def saved = ["c", "a", "b"].collect { tagRepository.save(new Tag(it)) }

        expect:
        tagRepository.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.desc("id"))))*.id == saved*.id.sort().reverse().take(2)
    }

    void "a single result query rejects several rows"() {
        given:
        ["same", "same"].each { tagRepository.save(new Tag(it)) }

        when:
        tagRepository.findByName("same")

        then:
        thrown(NonUniqueResultException)
    }

    void "relationship entities between the same nodes stay separate, and saving one again updates it"() {
        given:
        def keanu = new Person("Keanu", 1964)
        def movie = new Movie("The Matrix", "Sci-fi", 1999)
        movie.roles = [new Role(["Neo"], keanu), new Role(["Thomas"], keanu)]

        when:
        def saved = movieRepository.save(movie)

        then:
        saved.roles*.id.toSet().size() == 2
        roleNames() == [["Neo"], ["Thomas"]]

        when:
        saved.roles[0].roles[0] = "The One"
        movieRepository.update(saved)

        then:
        roleNames() == [["The One"], ["Thomas"]]
    }

    private List<Object> roleNames() {
        return operations.executeQuery("MATCH (:Person)-[r:ACTED_IN]->(:Movie) RETURN r.roles ORDER BY r.roles", [:], List)
    }

    void "changing a single valued relationship replaces the old one"() {
        given:
        def ada = new Employee("Ada")
        ada.manager = new Employee("Bob")
        employeeRepository.save(ada)

        when:
        ada.manager = new Employee("Carol")
        employeeRepository.save(ada)

        then:
        employeeRepository.findByName("Ada").get().manager.name == "Carol"
        operations.executeQuery("MATCH (:Employee {name: 'Ada'})-[:REPORTS_TO]->(m) RETURN m.name", [:], String) == ["Carol"]
    }

    void "a null single valued relationship keeps the one stored, as it may not have been fetched"() {
        given:
        def ada = new Employee("Ada")
        ada.manager = new Employee("Bob")
        employeeRepository.save(ada)

        when:
        employeeRepository.update(employeeRepository.findById("Ada").get())

        then:
        employeeRepository.findByName("Ada").get().manager.name == "Bob"
    }

    void "a new related node with an assigned identifier goes through the persist lifecycle"() {
        given:
        def ada = new Employee("Ada")
        ada.manager = new Employee("Bob")

        when:
        employeeRepository.save(ada)

        then:
        ada.manager.created != null

        when: "a saved one is linked again"
        def created = employeeRepository.findById("Bob").get().created
        ada.manager = employeeRepository.findById("Bob").get()
        employeeRepository.update(ada)

        then:
        employeeRepository.findById("Bob").get().created == created
    }

    void "a collection property absent from the node stays null"() {
        when:
        noteRepository.save(new Note("a", null))

        then:
        noteRepository.findById("a").get().tags() == null

        when:
        noteRepository.save(new Note("b", ["x"]))

        then:
        noteRepository.findById("b").get().tags() == ["x"]
    }

    void "bound parameters are encoded once"() {
        given:
        readingRepository.save(new Reading("a", 1 as Short))

        expect: "the encoder that adds one was applied to the value stored and the argument the same number of times"
        readingRepository.findByLevel(1 as Short)*.name == ["a"]
    }

    void "page an explicit query that limits a WITH clause, not one that limits its RETURN"() {
        given:
        ["c", "a", "b"].each { tagRepository.save(new Tag(it)) }

        expect: "the page is taken from the two tags the WITH clause keeps"
        tagRepository.findFirstTwo(Pageable.unpaged())*.name == ["a", "b"]
        tagRepository.findFirstTwo(Pageable.from(1, 1))*.name == ["b"]
        tagRepository.findFirstTwo(Pageable.from(2, 1)).empty

        when:
        tagRepository.findTwo(Pageable.from(0, 1))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("declares SKIP or LIMIT itself")
    }
}
