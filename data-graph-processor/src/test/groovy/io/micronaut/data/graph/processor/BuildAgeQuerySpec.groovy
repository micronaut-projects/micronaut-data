package io.micronaut.data.graph.processor

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import io.micronaut.data.graph.model.query.builder.AgeCypherQueryBuilder
import io.micronaut.data.graph.model.query.builder.AgeStatements
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class BuildAgeQuerySpec extends AbstractGraphDataSpec {

    static final String ENTITIES = '''
@Node
class Friend {
    @Id
    private String name;
    @Relationship("FRIENDS")
    private List<Friend> friends = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Friend> getFriends() { return friends; }
    public void setFriends(List<Friend> friends) { this.friends = friends; }
}

@Node
class Account {
    @Id
    @GeneratedValue
    private Long id;
    private String owner;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
}
'''

    /**
     * The movie entities of the Cypher specs with the single label Apache AGE allows a node.
     */
    static final String AGE_MOVIES = MOVIES.replace('@Node(labels = {"Person", "Actor"})', '@Node')

    @Shared
    BeanDefinition<?> friends

    @Shared
    BeanDefinition<?> accounts

    @Shared
    BeanDefinition<?> movies

    @Shared
    BeanDefinition<?> people

    void setupSpec() {
        assert AGE_MOVIES != MOVIES
        movies = buildRepository('test.MovieRepository', """
$AGE_MOVIES

@TestAgeRepository
interface MovieRepository extends CrudRepository<Movie, String> {
    List<Movie> findByReleasedGreaterThan(int year);
    Movie findByTitleAndReleased(String title, Integer released);
    List<Movie> findByTitleStartsWithOrderByReleasedDesc(String prefix);
    List<Movie> findByTitleContainsIgnoreCase(String part);
    List<Movie> findByTitleEndsWith(String suffix);
    List<Movie> findByReleasedBetween(int from, int to);
    List<Movie> findByReleasedIn(List<Integer> years);
    List<Movie> findByReleasedNotIn(List<Integer> years);
    List<Movie> findByDescriptionIsNull();
    List<Movie> findByTitleOrDescription(String title, String description);
    long countByReleasedGreaterThan(int year);
    boolean existsByTitle(String title);
    List<Movie> findByDirectorsName(String name);
    List<Movie> findByDirectorsNameOrderByDirectorsBorn(String name);
    List<Movie> findByDirectorsIsEmpty();
    List<Movie> findByRolesIsNotEmpty();
    @Join("directors")
    List<Movie> findByReleasedOrderByDirectorsNameDesc(Integer released);
    List<Movie> findByRolesPersonName(String name);
    List<Movie> findByRolesRolesArrayContains(String role);
    @Join("directors")
    List<Movie> findByReleased(Integer released);
    @Join("roles")
    @Join("directors")
    Optional<Movie> findByTitle(String title);
    List<String> findTitleByReleased(Integer released);
    Integer findMaxReleased();
    List<MovieSummary> findByReleasedLessThan(Integer released);
    void updateReleased(@Id String title, Integer released);
    void deleteByReleasedLessThan(Integer released);
    List<Movie> findTop3OrderByReleasedDesc();
    Page<Movie> findByReleasedGreaterThan(Integer released, Pageable pageable);
    @Query("MATCH (m:Movie) WHERE m.title CONTAINS \$title RETURN m")
    List<Movie> search(String title);
    @Query("MATCH (m:Movie {title: \$title}) SET m.votes = coalesce(m.votes, 0) + 1")
    int vote(String title);
    @Query("MATCH (m:Movie {title: \$title}) DETACH DELETE m")
    void purge(String title);
    @Query("MATCH (m:Movie) WHERE m.title = '\$notAParameter' AND m.released = \$released RETURN m")
    List<Movie> literal(Integer released);
    @Query("MATCH (m:Movie) WHERE m.released IN \$set RETURN count(m)")
    long countIn(List<Integer> set);
    @Query("MATCH (m:Movie {title: \$title}) SET m.set = \$title RETURN count(m)")
    int setSet(String title);
}
""")
        people = buildRepository('test.PersonRepository', """
$AGE_MOVIES

@TestAgeRepository
interface PersonRepository extends CrudRepository<Person, String> {
    List<Person> findByMentorName(String name);
    List<Person> findByMentor(Person mentor);
    Optional<Person> findByName(String name);
    @Join("mentor")
    List<Person> findByBornGreaterThan(Integer born);
}
""")
        friends = buildRepository('test.FriendRepository', """
import io.micronaut.data.graph.model.*;

$ENTITIES

@TestAgeRepository
interface FriendRepository extends CrudRepository<Friend, String> {
    Optional<GraphPath<Friend>> findShortestPathByFriends(String from, String to, @PathLimit int maxDepth);
    @PathLimit(3)
    List<GraphPath<Friend>> findAllShortestPaths(String from, String to);
    @PathLimit(3)
    boolean existsPath(String from, String to);
    @PathLimit(3)
    Page<GraphPath<Friend>> findPathByFriends(String from, String to, Pageable pageable);
    @PathLimit(3)
    Page<GraphPath<Friend>> findAllShortestPathsByFriends(String from, String to, Pageable pageable);
    List<Friend> findByNameStartsWith(String prefix);
}
""")
        accounts = buildRepository('test.AccountRepository', """
$ENTITIES

@TestAgeRepository
interface AccountRepository extends CrudRepository<Account, Long> {
    List<Account> findByOwner(String owner);
}
""")
    }

    @Unroll
    void "test AGE query for #method"() {
        expect:
        getQuery(findMethod(friends, method)) == AgeStatements.wrap(query)

        where:
        method                      | query
        'findShortestPathByFriends' | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[:FRIENDS*1..$__pathLimit]->(e) WITH p ORDER BY length(p) LIMIT 1 RETURN p'
        'findAllShortestPaths'      | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[*1..3]-(e) WITH s, e, min(length(p)) AS shortest MATCH q = (s)-[*1..3]-(e) WHERE length(q) = shortest RETURN q AS p'
        'existsPath'                | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[*1..3]-(e) RETURN true AS result LIMIT 1'
        'findByNameStartsWith'      | 'MATCH (n:Friend) WHERE n.name STARTS WITH $p0 RETURN n'
    }

    @Unroll
    void "test AGE movie query for #method"() {
        expect: "the Cypher of the Neo4j spec, with the single label of each node, wrapped in the SQL that runs it"
        getQuery(findMethod(movies, method, arity)) == query

        where:
        method                                     | arity | query
        'findByReleasedGreaterThan'                | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released > $p0 RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByTitleAndReleased'                   | 2     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE (n.title = $p0 AND n.released = $p1) RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByTitleStartsWithOrderByReleasedDesc' | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.title STARTS WITH $p0 RETURN n ORDER BY n.released DESC $$, ?) AS ("n" ag_catalog.agtype)'
        'findByTitleContainsIgnoreCase'            | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE toLower(n.title) CONTAINS toLower($p0) RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByTitleEndsWith'                      | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.title ENDS WITH $p0 RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByReleasedBetween'                    | 2     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE (n.released >= $p0 AND n.released <= $p1) RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByReleasedIn'                         | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released IN $p0 RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByReleasedNotIn'                      | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE NOT (n.released IN $p0) RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByDescriptionIsNull'                  | 0     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.tagline IS NULL RETURN n $$) AS ("n" ag_catalog.agtype)'
        'findByTitleOrDescription'                 | 2     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE (n.title = $p0 OR n.tagline = $p1) RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'countByReleasedGreaterThan'               | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released > $p0 RETURN count(n) AS result $$, ?) AS ("result" ag_catalog.agtype)'
        'existsByTitle'                            | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.title = $p0 RETURN true AS result LIMIT 1 $$, ?) AS ("result" ag_catalog.agtype)'
        'findByDirectorsName'                      | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie), (n)<-[:DIRECTED]-(a0:Person) WHERE a0.name = $p0 WITH DISTINCT n RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByDirectorsNameOrderByDirectorsBorn'  | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie), (n)<-[:DIRECTED]-(a0:Person) WHERE a0.name = $p0 WITH n, min(a0.born) AS o0 RETURN n ORDER BY o0 ASC $$, ?) AS ("n" ag_catalog.agtype)'
        'findByDirectorsIsEmpty'                   | 0     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE NOT (n)<-[:DIRECTED]-() RETURN n $$) AS ("n" ag_catalog.agtype)'
        'findByRolesIsNotEmpty'                    | 0     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE (n)<-[:ACTED_IN]-() RETURN n $$) AS ("n" ag_catalog.agtype)'
        'findByReleasedOrderByDirectorsNameDesc'   | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie), (n)<-[:DIRECTED]-(a0:Person) WHERE n.released = $p0 WITH n, max(a0.name) AS o0 OPTIONAL MATCH (n)<-[:DIRECTED]-(j0:Person) WITH n, o0, collect(j0) AS j_directors RETURN n, j_directors ORDER BY o0 DESC $$, ?) AS ("n" ag_catalog.agtype, "j_directors" ag_catalog.agtype)'
        'findByRolesPersonName'                    | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie), (n)<-[ar0:ACTED_IN]-(a0:Person) WHERE a0.name = $p0 WITH DISTINCT n RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByRolesRolesArrayContains'            | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie), (n)<-[ar0:ACTED_IN]-(a0:Person) WHERE $p0 IN ar0.roles WITH DISTINCT n RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByReleased'                           | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released = $p0 OPTIONAL MATCH (n)<-[:DIRECTED]-(j0:Person) WITH n, collect(j0) AS j_directors RETURN n, j_directors $$, ?) AS ("n" ag_catalog.agtype, "j_directors" ag_catalog.agtype)'
        'findByTitle'                              | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.title = $p0 OPTIONAL MATCH (n)<-[r0:ACTED_IN]-(j0:Person) WITH n, collect(CASE WHEN r0 IS NULL THEN NULL ELSE {rel: r0, node: j0} END) AS j_roles OPTIONAL MATCH (n)<-[:DIRECTED]-(j1:Person) WITH n, j_roles, collect(j1) AS j_directors RETURN n, j_roles, j_directors $$, ?) AS ("n" ag_catalog.agtype, "j_roles" ag_catalog.agtype, "j_directors" ag_catalog.agtype)'
        'findTitleByReleased'                      | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released = $p0 RETURN n.title AS title $$, ?) AS ("title" ag_catalog.agtype)'
        'findMaxReleased'                          | 0     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) RETURN max(n.released) AS result $$) AS ("result" ag_catalog.agtype)'
        'findByReleasedLessThan'                   | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released < $p0 RETURN n.title AS title, n.released AS released $$, ?) AS ("title" ag_catalog.agtype, "released" ag_catalog.agtype)'
        'updateReleased'                           | 2     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.title = $p0 SET n.released = $p1 RETURN count(*) $$, ?) AS ("count(*)" ag_catalog.agtype)'
        'deleteByReleasedLessThan'                 | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released < $p0 DETACH DELETE n RETURN count(*) $$, ?) AS ("count(*)" ag_catalog.agtype)'
        'findTop3OrderByReleasedDesc'              | 0     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) RETURN n ORDER BY n.released DESC LIMIT 3 $$) AS ("n" ag_catalog.agtype)'
        'count'                                    | 0     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) RETURN count(n) AS result $$) AS ("result" ag_catalog.agtype)'
        'findById'                                 | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.title = $p0 RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'existsById'                               | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.title = $p0 RETURN true AS result LIMIT 1 $$, ?) AS ("result" ag_catalog.agtype)'
        'deleteById'                               | 1     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.title = $p0 DETACH DELETE n RETURN count(*) $$, ?) AS ("count(*)" ag_catalog.agtype)'
        'deleteAll'                                | 0     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) DETACH DELETE n RETURN count(*) $$) AS ("count(*)" ag_catalog.agtype)'
        'findAll'                                  | 0     | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) RETURN n $$) AS ("n" ag_catalog.agtype)'
    }

    void "test the count query of a pageable AGE finder"() {
        given:
        ExecutableMethod<?, ?> method = findMethod(movies, 'findByReleasedGreaterThan', 2)

        expect:
        getQuery(method) == 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released > $p0 RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        method.annotationMetadata.stringValue(Query, 'countQuery').get() == 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Movie) WHERE n.released > $p0 RETURN count(n) AS result $$, ?) AS ("result" ag_catalog.agtype)'
    }

    @Unroll
    void "test explicit AGE query #method is a #operation"() {
        given:
        def m = findMethod(movies, method, 1)

        expect: "the query is wrapped, with a column per returned item and the parameter map when the query takes parameters"
        getRawQuery(m) == query
        m.annotationMetadata.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType).get() == operation

        where:
        method    | operation                       | query
        'search'  | DataMethod.OperationType.QUERY  | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (m:Movie) WHERE m.title CONTAINS $title RETURN m $$, ?) AS ("m" ag_catalog.agtype)'
        'vote'    | DataMethod.OperationType.UPDATE | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (m:Movie {title: $title}) SET m.votes = coalesce(m.votes, 0) + 1 RETURN count(*) $$, ?) AS ("count(*)" ag_catalog.agtype)'
        'purge'   | DataMethod.OperationType.DELETE | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (m:Movie {title: $title}) DETACH DELETE m $$, ?) AS ("result" ag_catalog.agtype)'
        'literal' | DataMethod.OperationType.QUERY  | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (m:Movie) WHERE m.title = \'$notAParameter\' AND m.released = $released RETURN m $$, ?) AS ("m" ag_catalog.agtype)'
        'countIn' | DataMethod.OperationType.QUERY  | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (m:Movie) WHERE m.released IN $set RETURN count(m) $$, ?) AS ("count(m)" ag_catalog.agtype)'
        'setSet'  | DataMethod.OperationType.UPDATE | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (m:Movie {title: $title}) SET m.set = $title RETURN count(m) $$, ?) AS ("count(m)" ag_catalog.agtype)'
    }

    void "test AGE parameters are bound by name"() {
        expect:
        getParameterNames(findMethod(movies, 'findByTitleAndReleased', 2)) == ['p0', 'p1']
        getParameterNames(findMethod(movies, 'search', 1)) == ['title']
        getParameterNames(findMethod(movies, 'literal', 1)) == ['released']
    }

    @Unroll
    void "test AGE person query for #method"() {
        expect: "generated identifiers are the graph ids of the nodes"
        getQuery(findMethod(people, method, 1)) == query

        where:
        method                   | query
        'findByMentorName'       | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Person), (n)-[:FOLLOWS]->(a0:Person) WHERE a0.name = $p0 WITH DISTINCT n RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByMentor'           | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Person), (n)-[:FOLLOWS]->(a0:Person) WHERE id(a0) = $p0 WITH DISTINCT n RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findByName'             | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Person) WHERE n.name = $p0 RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'findById'               | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Person) WHERE id(n) = $p0 RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        'existsById'             | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Person) WHERE id(n) = $p0 RETURN true AS result LIMIT 1 $$, ?) AS ("result" ag_catalog.agtype)'
        'deleteById'             | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Person) WHERE id(n) = $p0 DETACH DELETE n RETURN count(*) $$, ?) AS ("count(*)" ag_catalog.agtype)'
        'findByBornGreaterThan'  | 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Person) WHERE n.born > $p0 OPTIONAL MATCH (n)-[:FOLLOWS]->(j0:Person) WITH n, collect(j0) AS j_mentor RETURN n, j_mentor $$, ?) AS ("n" ag_catalog.agtype, "j_mentor" ag_catalog.agtype)'
    }

    void "test a page of a shortest path counts the shortest path"() {
        given:
        def shortest = findMethod(friends, 'findPathByFriends')
        def allShortest = findMethod(friends, 'findAllShortestPathsByFriends')

        expect: "the one shortest path is kept by a WITH clause, which the page limits and the count query counts"
        getQuery(shortest) == AgeStatements.wrap('MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[:FRIENDS*1..3]->(e) WITH p ORDER BY length(p) LIMIT 1 RETURN p')
        shortest.annotationMetadata.stringValue(Query, 'countQuery').get() ==
            AgeStatements.wrap('MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[:FRIENDS*1..3]->(e) WITH p ORDER BY length(p) LIMIT 1 RETURN count(p) AS result')

        and: "all shortest paths are matched again by another variable, which AGE needs, and counted"
        getQuery(allShortest) == AgeStatements.wrap('MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[:FRIENDS*1..3]->(e) WITH s, e, min(length(p)) AS shortest MATCH q = (s)-[:FRIENDS*1..3]->(e) WHERE length(q) = shortest RETURN q AS p')
        allShortest.annotationMetadata.stringValue(Query, 'countQuery').get() ==
            AgeStatements.wrap('MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[:FRIENDS*1..3]->(e) WITH s, e, min(length(p)) AS shortest MATCH q = (s)-[:FRIENDS*1..3]->(e) WHERE length(q) = shortest RETURN count(q) AS result')
    }

    void "test database assigned identifiers are graph ids"() {
        expect:
        getQuery(findMethod(accounts, 'findById')) == 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (n:Account) WHERE id(n) = $p0 RETURN n $$, ?) AS ("n" ag_catalog.agtype)'
        getQuery(findMethod(accounts, 'findByOwner')) == AgeStatements.wrap('MATCH (n:Account) WHERE n.owner = $p0 RETURN n')
    }

    void "test explicit queries are wrapped and checked at build time"() {
        when:
        def repository = buildRepository('test.ExplicitRepository', """
$ENTITIES

@TestAgeRepository
interface ExplicitRepository extends CrudRepository<Friend, String> {
    @Query("MATCH (f:Friend) WHERE f.name = \$name RETURN f.name AS name, count(f) AS total")
    List<Map<String, Object>> counts(String name);
}
""")

        then:
        findMethod(repository, 'counts').stringValue(Query, 'rawQuery').get() == 'SELECT * FROM ag_catalog.cypher(\'$__graph\', $$ MATCH (f:Friend) WHERE f.name = $name RETURN f.name AS name, count(f) AS total $$, ?) AS ("name" ag_catalog.agtype, "total" ag_catalog.agtype)'

        when:
        buildRepository('test.StarRepository', """
$ENTITIES

@TestAgeRepository
interface StarRepository extends CrudRepository<Friend, String> {
    @Query("MATCH (f:Friend) RETURN *")
    List<Friend> everything();
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('RETURN * is not supported with Apache AGE')
    }

    void "test path queries need a maximum depth"() {
        when:
        buildRepository('test.UnboundedRepository', """
import io.micronaut.data.graph.model.*;

$ENTITIES

@TestAgeRepository
interface UnboundedRepository extends CrudRepository<Friend, String> {
    Optional<GraphPath<Friend>> findShortestPath(String from, String to);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('needs a maximum depth for path queries')
    }

    void "test nodes have a single label"() {
        when:
        buildRepository('test.LabelsRepository', """
@Node(labels = {"Person", "Actor"})
class Person {
    @Id
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@TestAgeRepository
interface LabelsRepository extends CrudRepository<Person, String> {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Apache AGE nodes have a single label')
    }

    void "test dynamic relationships are rejected"() {
        when:
        buildRepository('test.DynamicRepository', """
@Node
class Pet {
    @Id
    private String name;
    @DynamicRelationships
    private Map<String, List<Object>> related = new HashMap<>();
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, List<Object>> getRelated() { return related; }
    public void setRelated(Map<String, List<Object>> related) { this.related = related; }
}

@TestAgeRepository
interface DynamicRepository extends CrudRepository<Pet, String> {
    List<Pet> findByNameStartsWith(String prefix);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Dynamic relationships are not supported by Apache AGE')
    }

    void "test temporal literals stay strings, as AGE stores them"() {
        given:
        def builder = new AgeCypherQueryBuilder()

        expect:
        builder.asLiteral(LocalDate.of(2024, 1, 31)) == "'2024-01-31'"
        builder.asLiteral(Instant.parse("2024-01-31T10:15:30Z")) == "'2024-01-31T10:15:30Z'"
        builder.asLiteral(Duration.ofMinutes(90)) == "'PT1H30M'"
        builder.asLiteral([LocalDate.of(2024, 1, 31), 1]) == "['2024-01-31', 1]"
        builder.asLiteral(42) == '42'
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> definition, String name) {
        def method = definition.executableMethods.find { it.methodName == name }
        assert method != null: "No method $name"
        return method
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> definition, String name, int arity) {
        def method = definition.executableMethods.find { it.methodName == name && it.argumentTypes.length == arity }
        assert method != null: "No method $name with $arity arguments"
        return method
    }
}
