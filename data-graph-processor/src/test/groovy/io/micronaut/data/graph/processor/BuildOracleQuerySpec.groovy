package io.micronaut.data.graph.processor

import io.micronaut.data.annotation.Query
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

class BuildOracleQuerySpec extends AbstractGraphDataSpec {

    static final String COLUMNS = 'n.title AS "n__ID_", n.title AS "n__title", \'Movie\' AS "n__LABEL_", n.tagline AS "n__tagline", n.released AS "n__released"'

    @Shared
    BeanDefinition<?> pgql

    @Shared
    BeanDefinition<?> sql

    @Shared
    BeanDefinition<?> paths

    void setupSpec() {
        pgql = buildRepository('test.PgqlRepository', """
$MOVIES

@TestOracleRepository
@PropertyGraph("movies")
interface PgqlRepository extends GenericRepository<Movie, String> {
    Optional<Movie> findById(String id);
    List<Movie> findByReleasedGreaterThanOrderByReleased(int year);
    List<Movie> findByReleasedIn(List<Integer> years);
    long countByReleasedLessThan(int year);
    boolean existsByTitle(String title);
    List<Movie> findByDirectorsName(String name);
    List<Movie> findByRolesPersonName(String name);
    List<String> findTitleByReleasedGreaterThan(int released);
    Integer findMaxReleased();
    List<MovieSummary> findByReleasedLessThan(Integer released);
    List<Movie> findByTitleRegex(String pattern);
    @Query("SELECT m.title AS title FROM MATCH (m:Movie) ON movies WHERE m.released > \$year AND m.released < \$year + 10")
    List<String> titles(int year);
}
""")
        sql = buildRepository('test.SqlRepository', """
$MOVIES

@TestOracleRepository
@PropertyGraph(value = "movies", language = PropertyGraph.QueryLanguage.SQL_PGQ)
interface SqlRepository extends GenericRepository<Movie, String> {
    Optional<Movie> findById(String id);
    List<Movie> findByReleasedGreaterThanOrderByReleased(int year);
    List<Movie> findByTitleStartsWithIgnoreCase(String prefix);
    List<Movie> findByTitleContains(String part);
    long countByReleasedLessThan(int year);
    boolean existsByTitle(String title);
    List<Movie> findByDirectorsName(String name);
    List<Movie> findByRolesRolesIsNotNull();
    List<String> findTitleByReleasedGreaterThan(int released);
    Integer findMaxReleased();
    List<MovieSummary> findByReleasedLessThan(Integer released);
    @Query("SELECT COUNT(*) AS result FROM v\$session WHERE sid = \$sid AND path <> 'C:\\\\' -- see \$ticket")
    long sessions(long sid);
}
""")
        paths = buildRepository('test.PathRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestOracleRepository
@PropertyGraph("people")
interface PathRepository extends GenericRepository<Person, String> {
    @PathLimit(5)
    Optional<GraphPath<Person>> findShortestPathByMentor(String from, String to);
    @PathLimit(5)
    List<GraphPath<Person>> findAllShortestPathsByMentor(String from, String to);
    @PathLimit(5)
    boolean existsPathByMentor(String from, String to);
    @PathLimit(5)
    long countPaths(String from, String to);
    @PathLimit(5)
    long countPathsByMentor(String from, String to, @PathLimit int depth);
    Optional<GraphPath<Person>> findShortestPath(String from, String to, @PathLimit int depth);
    List<GraphRelationship<Person, Person>> findRelationshipsByMentor(String from, String to);
}
""")
    }

    @Unroll
    void "test PGQL for #method"() {
        expect:
        getQuery(findMethod(pgql, method)) == query

        where:
        method                                     | query
        'findById'                                 | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.title = ?)"
        'findByReleasedGreaterThanOrderByReleased' | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.released > ?) ORDER BY n.released ASC"
        'findByReleasedIn'                         | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.released IN (?))"
        'countByReleasedLessThan'                  | 'SELECT COUNT(*) AS "result" FROM MATCH (n:Movie) ON movies WHERE (n.released < ?)'
        'existsByTitle'                            | 'SELECT TRUE AS "result" FROM MATCH (n:Movie) ON movies WHERE (n.title = ?) LIMIT 1'
        'findByDirectorsName'                      | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (a0.name = ?)"
        'findByRolesPersonName'                    | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (a0.name = ?)"
        'findTitleByReleasedGreaterThan'           | 'SELECT n.title AS "title" FROM MATCH (n:Movie) ON movies WHERE (n.released > ?)'
        'findMaxReleased'                          | 'SELECT MAX(n.released) AS "result" FROM MATCH (n:Movie) ON movies'
        'findByReleasedLessThan'                   | 'SELECT n.title AS "title", n.released AS "released" FROM MATCH (n:Movie) ON movies WHERE (n.released < ?)'
        'findByTitleRegex'                         | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (JAVA_REGEXP_LIKE(n.title, ?))"
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
        'findTitleByReleasedGreaterThan'           | 'SELECT "c0" AS "title" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released > ?) COLUMNS (n.title AS "c0"))'
        'findMaxReleased'                          | 'SELECT MAX("c0") AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) COLUMNS (n.released AS "c0"))'
        'findByReleasedLessThan'                   | 'SELECT "c0" AS "title", "c1" AS "released" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released < ?) COLUMNS (n.title AS "c0", n.released AS "c1"))'
    }

    void "test explicit queries take positional parameters"() {
        given:
        def method = findMethod(pgql, 'titles')

        expect:
        method.annotationMetadata.stringValue(Query, 'rawQuery').get() == 'SELECT m.title AS title FROM MATCH (m:Movie) ON movies WHERE m.released > ? AND m.released < ? + 10'
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
@PropertyGraph(value = "movies", language = PropertyGraph.QueryLanguage.SQL_PGQ)
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

    void "test PGQL path finders"() {
        given:
        def person = 's.id AS "%1$s__ID_", s.id AS "%1$s__id", \'Person\' AS "%1$s__LABEL_", s.name AS "%1$s__name", s.born AS "%1$s__born"'
        def v1 = 'v1.id AS "v1__ID_", v1.id AS "v1__id", \'Person\' AS "v1__LABEL_", v1.name AS "v1__name", v1.born AS "v1__born"'
        def v2 = 'v2.id AS "v2__ID_", v2.id AS "v2__id", \'Person\' AS "v2__LABEL_", v2.name AS "v2__name", v2.born AS "v2__born"'
        def steps = "MATCHNUM(v1) AS \"p__M_\", ELEMENT_NUMBER(x) AS \"p__I_\", $v1, $v2, 'FOLLOWS' AS \"x__TYPE_\""

        expect: "the shortest kinds exclude the empty path from a node to itself, as the Cypher builder does"
        getQuery(findMethod(paths, 'findShortestPathByMentor')) ==
            "SELECT $steps FROM MATCH ANY SHORTEST (s:Person) -[x:FOLLOWS]->{1,5} (t:Person) ON people ONE ROW PER STEP (v1, x, v2) WHERE s.id = ? AND t.id = ? AND s.id <> t.id ORDER BY MATCHNUM(v1), ELEMENT_NUMBER(x)"
        getQuery(findMethod(paths, 'findAllShortestPathsByMentor')).contains("FROM MATCH ALL SHORTEST (s:Person) -[x:FOLLOWS]->{1,5} (t:Person) ON people")
        getQuery(findMethod(paths, 'existsPathByMentor')) ==
            'SELECT 1 AS "result" FROM MATCH ANY (s:Person) -[:FOLLOWS]->{1,5} (t:Person) ON people WHERE s.id = ? AND t.id = ? AND s.id <> t.id LIMIT 1'

        and: "all paths from a node to itself are its cycles"
        getQuery(findMethod(paths, 'countPaths')) ==
            'SELECT COUNT(*) AS "result" FROM MATCH ALL (s:Person) -[]-{1,5} (t:Person) ON people WHERE s.id = ? AND t.id = ?'

        and: "a @PathLimit argument is rendered by the runtime in place of the token, over the depth of the annotation"
        getQuery(findMethod(paths, 'countPathsByMentor')) ==
            'SELECT COUNT(*) AS "result" FROM MATCH ALL (s:Person) -[:FOLLOWS]->{1,$__pathLimit} (t:Person) ON people WHERE s.id = ? AND t.id = ?'

        and: "a path over any relationship type reads the type of each step from the label of its edge"
        getQuery(findMethod(paths, 'findShortestPath')) ==
            "SELECT ${steps.replace("'FOLLOWS' AS", 'LABEL(x) AS')} FROM MATCH ANY SHORTEST (s:Person) -[x]-{1,\$__pathLimit} (t:Person) ON people ONE ROW PER STEP (v1, x, v2) WHERE s.id = ? AND t.id = ? AND s.id <> t.id ORDER BY MATCHNUM(v1), ELEMENT_NUMBER(x)"
        getQuery(findMethod(paths, 'findRelationshipsByMentor')) ==
            "SELECT ${String.format(person, 's')}, ${String.format(person, 's').replace('s.', 't.').replace('"s__', '"t__')}, 'FOLLOWS' AS \"r__TYPE_\" FROM MATCH (s:Person) -[r:FOLLOWS]-> (t:Person) ON people WHERE s.id = ? AND t.id = ?"
    }

    @Unroll
    void "test unsupported #description"() {
        when:
        buildRepository('test.UnsupportedRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestOracleRepository
@PropertyGraph(value = "movies"$language)
interface UnsupportedRepository extends GenericRepository<Movie, String> {
    $method
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        description            | language                                              | method                                                              | message
        'writes'               | ''                                                    | 'void deleteByTitle(String title);'                                 | 'read only views over tables'
        'LIKE in PGQL'         | ''                                                    | 'List<Movie> findByTitleStartsWith(String prefix);'                 | 'PGQL has no LIKE'
        'fetch joins'          | ''                                                    | '@Join("directors") List<Movie> findByReleased(Integer released);' | 'not supported for Oracle property graphs'
        'paths in SQL/PGQ'     | ', language = PropertyGraph.QueryLanguage.SQL_PGQ'    | '@PathLimit(3) Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'use @PropertyGraph(language = PGQL)'
        'unbounded paths'      | ''                                                    | 'Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'need a maximum depth'
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> definition, String name) {
        def method = definition.executableMethods.find { it.methodName == name }
        assert method != null: "No method $name"
        return method
    }
}
