package io.micronaut.data.graph.processor

import io.micronaut.data.annotation.Query
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

class BuildOracleQuerySpec extends AbstractGraphDataSpec {

    static final String COLUMNS = 'n.title AS "n__ID_", n.title AS "n__title", \'Movie\' AS "n__LABEL_", n.tagline AS "n__tagline", n.released AS "n__released"'

    @Shared
    BeanDefinition<?> sql

    @Shared
    BeanDefinition<?> paths

    void setupSpec() {
        sql = buildRepository('test.SqlRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestOracleRepository
@PropertyGraph(value = "movies")
interface SqlRepository extends GenericRepository<Movie, String> {
    Optional<Movie> findById(String id);
    List<Movie> findByReleasedGreaterThanOrderByReleased(int year);
    List<Movie> findByTitleStartsWithIgnoreCase(String prefix);
    List<Movie> findByTitleContains(String part);
    long countByReleasedLessThan(int year);
    boolean existsByTitle(String title);
    List<Movie> findByDirectorsName(String name);
    List<Movie> findByRolesRolesIsNotNull();
    List<Movie> findByRolesIsNotNull();
    List<Movie> findByRoles(Role role);
    List<Movie> findByRolesPerson(Person person);
    @Join(value = "directors", type = Join.Type.INNER)
    List<Movie> findByTitle(String title);
    @Join(value = "directors", type = Join.Type.INNER)
    long countByTitle(String title);
    @Join(value = "directors", type = Join.Type.INNER)
    List<String> findTitleByReleased(Integer released);
    List<String> findTitleByReleasedGreaterThan(int released);
    Integer findMaxReleased();
    List<MovieSummary> findByReleasedLessThan(Integer released);
    @Join(value = "directors", type = Join.Type.INNER)
    Page<Movie> findByReleasedGreaterThanEquals(Integer released, Pageable pageable);
    @Join(value = "directors", type = Join.Type.LEFT)
    Page<Movie> findByReleasedLessThanEquals(Integer released, Pageable pageable);
    @Query("SELECT title FROM GRAPH_TABLE (movies MATCH (m IS Movie) WHERE m.released > \$year AND m.released < \$year + 10 COLUMNS (m.title AS title))")
    List<String> titles(int year);
    @PathLimit(2)
    List<GraphPath<Movie>> findAllPathsByRolesOrDirectors(String from, String to);
    @Query("SELECT COUNT(*) AS result FROM v\$session WHERE sid = \$sid AND path <> 'C:\\\\' -- see \$ticket")
    long sessions(long sid);
}
""")
        paths = buildRepository('test.PathRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestOracleRepository
@PropertyGraph(value = "people")
interface PathRepository extends GenericRepository<Person, String> {
    @PathLimit(5)
    boolean existsPathByMentor(String from, String to);
    @PathLimit(5)
    long countPaths(String from, String to);
    @PathLimit(5)
    long countPathsByMentor(String from, String to, @PathLimit int depth);
    @PathLimit(5)
    List<GraphPath<Person>> findAllPathsByMentor(String from, String to);
    List<GraphRelationship<Person, Person>> findRelationshipsByMentor(String from, String to);
}
""")
    }

    @Unroll
    void "test SQL/PGQ for #method"() {
        expect:
        getQuery(findMethod(sql, method)) == query

        where:
        method                                     | query
        'findById'                                 | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title = ?) COLUMNS ($COLUMNS))"
        'findByReleasedGreaterThanOrderByReleased' | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released > ?) COLUMNS ($COLUMNS)) ORDER BY \"n__released\" ASC"
        'findByTitleStartsWithIgnoreCase'          | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (LOWER(n.title) LIKE LOWER(?) || '%') COLUMNS ($COLUMNS))"
        'findByTitleContains'                      | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title LIKE '%' || ? || '%') COLUMNS ($COLUMNS))"
        'countByReleasedLessThan'                  | 'SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released < ?) COLUMNS (n.title AS "c0"))'
        'existsByTitle'                            | 'SELECT TRUE AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title = ?) COLUMNS (n.title AS "c0")) FETCH NEXT 1 ROWS ONLY'
        'findByDirectorsName'                      | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (a0.name = ?) COLUMNS ($COLUMNS))"
        'findByRolesRolesIsNotNull'                | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.roles IS NOT NULL) COLUMNS ($COLUMNS))"
        'findByRolesIsNotNull'                     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.id IS NOT NULL) COLUMNS ($COLUMNS))"
        'findByRoles'                              | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.id = ?) COLUMNS ($COLUMNS))"
        'findByRolesPerson'                        | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (a0.id = ?) COLUMNS ($COLUMNS))"
        'findByTitle'                              | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (n.title = ?) COLUMNS ($COLUMNS))"
        'countByTitle'                             | 'SELECT COUNT(DISTINCT "c0") AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (n.title = ?) COLUMNS (n.title AS "c0"))'
        'findTitleByReleased'                      | 'SELECT "c0" AS "title" FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (n.released = ?) COLUMNS (n.title AS "c0"))'
        'findTitleByReleasedGreaterThan'           | 'SELECT "c0" AS "title" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released > ?) COLUMNS (n.title AS "c0"))'
        'findMaxReleased'                          | 'SELECT MAX("c0") AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) COLUMNS (n.released AS "c0"))'
        'findByReleasedLessThan'                   | 'SELECT "c0" AS "title", "c1" AS "released" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released < ?) COLUMNS (n.title AS "c0", n.released AS "c1"))'
    }

    @Unroll
    void "test the page and its total of #method agree on the join"() {
        given:
        def m = findMethod(sql, method)

        expect:
        getQuery(m) == query
        m.annotationMetadata.stringValue(Query, 'countQuery').get() == countQuery

        where:
        method                            | query                                                                                                                       | countQuery
        'findByReleasedGreaterThanEquals' | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (n.released >= ?) COLUMNS ($COLUMNS))" | 'SELECT COUNT(DISTINCT "c0") AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (n.released >= ?) COLUMNS (n.title AS "c0"))'
        'findByReleasedLessThanEquals'    | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released <= ?) COLUMNS ($COLUMNS))" | 'SELECT COUNT(DISTINCT "c0") AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released <= ?) COLUMNS (n.title AS "c0"))'
    }

    void "test a relationship entity criterion binds the identifier of what it compares"() {
        expect:
        getParameterProperties(findMethod(sql, 'findByRoles')) == ['roles']
        getParameterProperties(findMethod(sql, 'findByRolesPerson')) == ['roles.person']
    }

    void "test a criterion on a relationship entity without an id compares the target node"() {
        given:
        def repository = buildRepository('test.CastRepository', """
$MOVIES

@RelationshipProperties
class Cameo {
    @TargetNode
    private Person person;
    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }
}

@Node
class Cast {
    @Id
    private String id;
    @Relationship(type = "APPEARS_IN", direction = Direction.INCOMING)
    private List<Cameo> cameos = new ArrayList<>();
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<Cameo> getCameos() { return cameos; }
    public void setCameos(List<Cameo> cameos) { this.cameos = cameos; }
}

@TestOracleRepository
@PropertyGraph("movies")
interface CastRepository extends GenericRepository<Cast, String> {
    List<Cast> findByCameosIsNotNull();
}
""")

        expect:
        getQuery(findMethod(repository, 'findByCameosIsNotNull')).contains('(n) <-[ar0 IS APPEARS_IN]- (a0 IS Person) WHERE (a0.id IS NOT NULL)')
    }

    void "test explicit queries take positional parameters"() {
        given:
        def method = findMethod(sql, 'titles')

        expect:
        method.annotationMetadata.stringValue(Query, 'rawQuery').get() == 'SELECT title FROM GRAPH_TABLE (movies MATCH (m IS Movie) WHERE m.released > ? AND m.released < ? + 10 COLUMNS (m.title AS title))'
        getParameterNames(method) == ['p0', 'p1']
    }

    void "test a dollar inside a name, a SQL comment and a backslash in a literal are not parameters"() {
        given:
        def method = findMethod(sql, 'sessions')

        expect:
        def rawQuery = method.annotationMetadata.stringValue(Query, 'rawQuery').get()
        rawQuery.startsWith("SELECT COUNT(*) AS result FROM v\$session WHERE sid = ? AND path <> 'C:\\")
        rawQuery.endsWith("' -- see \$ticket")
        getParameterNames(method) == ['p0']
    }

    void "test names that need quoting use double quotes"() {
        given:
        def repository = buildRepository('test.QuotedRepository', """
@Node("Movie Node")
class Quoted {
    @Id
    private String title;
    @MappedProperty("first name")
    private String firstName;
    @Relationship(type = "ACTED IN", direction = Direction.INCOMING)
    private List<Quoted> cast = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public List<Quoted> getCast() { return cast; }
    public void setCast(List<Quoted> cast) { this.cast = cast; }
}

@TestOracleRepository
@PropertyGraph(value = "movies")
interface QuotedRepository extends GenericRepository<Quoted, String> {
    List<Quoted> findByFirstName(String name);
    List<Quoted> findByCastTitle(String title);
}
""")

        expect:
        getQuery(findMethod(repository, 'findByFirstName')).contains('MATCH (n IS "Movie Node") WHERE (n."first name" = ?)')
        getQuery(findMethod(repository, 'findByCastTitle')).contains('MATCH (n IS "Movie Node"), (n) <-[IS "ACTED IN"]- (a0 IS "Movie Node") WHERE (a0.title = ?)')
    }

    void "test a property graph name must be a plain identifier"() {
        when:
        buildRepository('test.HostileGraphRepository', """
$MOVIES

@TestOracleRepository
@PropertyGraph("movies MATCH (x) COLUMNS (1 AS y)) --")
interface HostileGraphRepository extends GenericRepository<Movie, String> {
    long countByReleasedLessThan(int year);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('is not a plain identifier')
    }

    void "test a node without an identifier is rejected"() {
        when:
        buildRepository('test.NoIdRepository', """
@Node
class Anonymous {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@TestOracleRepository
@PropertyGraph("movies")
interface NoIdRepository extends GenericRepository<Anonymous, String> {
    long countByName(String name);
}
""")

        then: "the build fails with a message rather than a NullPointerException"
        def e = thrown(RuntimeException)
        e.message.contains("doesn't have an identity") || e.message.contains('has no identifier')
    }

    void "test SQL/PGQ path finders"() {
        expect: "the shortest kinds exclude the empty path from a node to itself, as the Cypher builder does"
        getQuery(findMethod(paths, 'existsPathByMentor')) ==
            'SELECT 1 AS "result" FROM GRAPH_TABLE (people MATCH (s IS Person) -[e IS FOLLOWS]->{1,5} (t IS Person) WHERE s.id = ? AND t.id = ? AND s.id <> t.id AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0")) FETCH NEXT 1 ROWS ONLY'

        and: "all paths from a node to itself are its cycles, each taking an edge once, as a Cypher path does"
        getQuery(findMethod(paths, 'countPaths')) ==
            'SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (people MATCH (s IS Person) -[e]-{1,5} (t IS Person) WHERE s.id = ? AND t.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0"))'

        and: "a @PathLimit argument is rendered by the runtime in place of the token, over the depth of the annotation"
        getQuery(findMethod(paths, 'countPathsByMentor')) ==
            'SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (people MATCH (s IS Person) -[e IS FOLLOWS]->{1,$__pathLimit} (t IS Person) WHERE s.id = ? AND t.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0"))'

        and: "the steps of every path are rows, numbered by their match and position"
        def allPaths = getQuery(findMethod(paths, 'findAllPathsByMentor'))
        allPaths.startsWith('SELECT * FROM GRAPH_TABLE (people MATCH (s IS Person) -[e IS FOLLOWS]->{1,5} (t IS Person) WHERE s.id = ? AND t.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) ONE ROW PER STEP (v1, x, v2) COLUMNS (MATCHNUM() AS "p__M_", ELEMENT_NUMBER(x) AS "p__I_", ')
        allPaths.endsWith(''''FOLLOWS' AS "x__TYPE_")) ORDER BY "p__M_", "p__I_"''')

        and: "a path over several relationship types tests the label of each step"
        getQuery(findMethod(sql, 'findAllPathsByRolesOrDirectors')).endsWith('''CASE WHEN x IS LABELED ACTED_IN THEN 'ACTED_IN' WHEN x IS LABELED DIRECTED THEN 'DIRECTED' END AS "x__TYPE_")) ORDER BY "p__M_", "p__I_"''')
    }

    void "test relationship finders bind the source and destination of the edge"() {
        when:
        def repository = buildRepository('test.FollowerRepository', """
import io.micronaut.data.graph.model.*;

@Node
class Follower {
    @Id
    private String id;
    @Relationship(type = "FOLLOWS", direction = Direction.INCOMING)
    private List<Follower> followers = new ArrayList<>();
    @Relationship(type = "KNOWS", direction = Direction.UNDIRECTED)
    private List<Follower> known = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<Follower> getFollowers() { return followers; }
    public void setFollowers(List<Follower> followers) { this.followers = followers; }
    public List<Follower> getKnown() { return known; }
    public void setKnown(List<Follower> known) { this.known = known; }
}

@TestOracleRepository
@PropertyGraph("people")
interface FollowerRepository extends GenericRepository<Follower, String> {
    List<GraphRelationship<Follower, Follower>> findRelationshipsByFollowers(String from, String to);
    List<GraphRelationship<Follower, Follower>> findRelationshipsByKnown(String from, String to);
}
""")

        then: "s stays the source of an incoming edge, bound to the far end"
        getQuery(findMethod(repository, 'findRelationshipsByFollowers')).contains(
            "MATCH (t IS Follower) <-[r IS FOLLOWS]- (s IS Follower) WHERE t.id = ? AND s.id = ?")

        and: "an undirected edge with both ends known is matched either way"
        getQuery(findMethod(repository, 'findRelationshipsByKnown')).contains(
            "MATCH (s IS Follower) -[r IS KNOWS]- (t IS Follower) WHERE s.id = ? AND t.id = ?")

        and: "the rows say which end of an undirected edge is its source"
        getQuery(findMethod(repository, 'findRelationshipsByKnown')).contains('CASE WHEN s IS SOURCE OF r THEN 1 ELSE 0 END AS "r__FORWARD_"')
        !getQuery(findMethod(repository, 'findRelationshipsByFollowers')).contains('r__FORWARD_')
    }

    @Unroll
    void "test unsupported #description"() {
        when:
        buildRepository('test.UnsupportedRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestOracleRepository
@PropertyGraph("movies")
interface UnsupportedRepository extends GenericRepository<Movie, String> {
    $method
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        description            | method                                                              | message
        'writes'               | 'void deleteByTitle(String title);'                                 | 'read only views over tables'
        'IsEmpty on a relationship' | 'List<Movie> findByDirectorsIsEmpty();'                             | 'Expected a string expression'
        'IsEmpty on a relationship identifier' | 'List<Movie> findByDirectorsIdIsEmpty();'                           | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a relationship' | 'List<Movie> findByDirectorsIsNull();'                              | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a relationship entity' | 'List<Movie> findByRolesIsNull();'                       | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a nested to-one relationship' | 'List<Movie> findByDirectorsMentorIsNull();'                        | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'sort on a relationship' | 'List<Movie> findByReleasedOrderByDirectorsBorn(Integer released);' | 'Sorting on a relationship is not supported for Oracle property graphs'
        'sort on a bound relationship' | 'List<Movie> findByDirectorsNameOrderByDirectorsBorn(String name);' | 'Sorting on a relationship is not supported for Oracle property graphs'
        'fetch joins'          | '@Join("directors") List<Movie> findByReleased(Integer released);' | 'not supported for Oracle property graphs'
        'shortest paths in SQL/PGQ' | '@PathLimit(3) Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'Shortest paths are not supported by the SQL/PGQ dialect'
        'any-type paths in SQL/PGQ' | '@PathLimit(3) List<GraphPath<Movie>> findAllPaths(String from, String to);' | 'Paths over any relationship type are not supported by the SQL/PGQ dialect'
        'unbounded paths'      | 'Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'need a maximum depth'
    }

    void "test schemaless properties are rejected"() {
        when:
        buildRepository('test.PetRepository', """
import io.micronaut.data.graph.annotation.DynamicAttributes;
import java.util.Map;

@Node
class Pet {
    @Id
    private String name;
    @DynamicAttributes
    private Map<String, Object> attributes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}

@TestOracleRepository
@PropertyGraph("pets")
interface PetRepository extends GenericRepository<Pet, String> {
    List<Pet> findByName(String name);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Schemaless properties and dynamic labels are not supported by Oracle property graphs')
    }

    void "test IsNull on a to-one relationship is rejected"() {
        when:
        buildRepository('test.MentorRepository', """
$MOVIES

@TestOracleRepository
@PropertyGraph("people")
interface MentorRepository extends GenericRepository<Person, String> {
    List<Person> findByMentorIsNull();
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Testing that a relationship is absent is not supported for Oracle property graphs')
        e.message.contains('NOT EXISTS outside GRAPH_TABLE')
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> definition, String name) {
        def method = definition.executableMethods.find { it.methodName == name }
        assert method != null: "No method $name"
        return method
    }
}
