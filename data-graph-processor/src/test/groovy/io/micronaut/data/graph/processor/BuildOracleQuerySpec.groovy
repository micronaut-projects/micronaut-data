package io.micronaut.data.graph.processor

import io.micronaut.data.annotation.Query
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

class BuildOracleQuerySpec extends AbstractGraphDataSpec {

    static final String COLUMNS = 'n.title AS "n__ID_", n.title AS "n__title", \'Movie\' AS "n__LABEL_", n.tagline AS "n__tagline", n.released AS "n__released"'

    static final String PERSON_COLUMNS = 'n.id AS "n__ID_", n.id AS "n__id", \'Person\' AS "n__LABEL_", n.name AS "n__name", n.born AS "n__born"'

    @Shared
    BeanDefinition<?> pgql

    @Shared
    BeanDefinition<?> sql

    @Shared
    BeanDefinition<?> paths

    @Shared
    BeanDefinition<?> moviePaths

    void setupSpec() {
        pgql = buildRepository('test.PgqlRepository', """
$MOVIES

@TestOracleRepository
@PropertyGraph(value = "movies", language = PropertyGraph.QueryLanguage.PGQL)
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
    Movie findByTitleAndReleased(String title, Integer released);
    List<Movie> findByReleasedBetween(int from, int to);
    List<Movie> findByReleasedNotIn(List<Integer> years);
    List<Movie> findByDescriptionIsNull();
    List<Movie> findByTitleOrDescription(String title, String description);
    List<Movie> findByDirectorsNameOrderByDirectorsBorn(String name);
    List<Movie> findByDirectorsIsEmpty();
    List<Movie> findByRolesIsNotEmpty();
    void updateReleased(@Id String title, Integer released);
    void deleteByReleasedLessThan(Integer released);
    List<Movie> findTop3OrderByReleasedDesc();
    Page<Movie> findByReleasedGreaterThan(Integer released, Pageable pageable);
    List<Movie> findAll();
    long count();
    void deleteById(String id);
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
    List<Movie> findByTitleEndsWith(String suffix);
    List<Movie> findByReleasedIn(List<Integer> years);
    Movie findByTitleAndReleased(String title, Integer released);
    List<Movie> findByReleasedBetween(int from, int to);
    List<Movie> findByReleasedNotIn(List<Integer> years);
    List<Movie> findByDescriptionIsNull();
    List<Movie> findByTitleOrDescription(String title, String description);
    List<Movie> findByDirectorsNameOrderByDirectorsBorn(String name);
    List<Movie> findByDirectorsIsEmpty();
    List<Movie> findByRolesIsNotEmpty();
    void updateReleased(@Id String title, Integer released);
    void deleteByReleasedLessThan(Integer released);
    List<Movie> findTop3OrderByReleasedDesc();
    Page<Movie> findByReleasedGreaterThan(Integer released, Pageable pageable);
    List<Movie> findAll();
    long count();
    void deleteById(String id);
    @Query("SELECT COUNT(*) AS result FROM v\$session WHERE sid = \$sid AND path <> 'C:\\\\' -- see \$ticket")
    long sessions(long sid);
}
""")
        paths = buildRepository('test.PathRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestOracleRepository
@PropertyGraph(value = "people", language = PropertyGraph.QueryLanguage.PGQL)
interface PathRepository extends GenericRepository<Person, String> {
    @PathLimit(5)
    Optional<GraphPath<Person>> findShortestPathByMentor(String from, String to);
    @PathLimit(5)
    List<GraphPath<Person>> findAllShortestPathsByMentor(String from, String to);
    @PathLimit(5)
    boolean existsPathByMentor(String from, String to);
    @PathLimit(5)
    long countPaths(String from, String to);
    List<GraphRelationship<Person, Person>> findRelationshipsByMentor(String from, String to);
    @PathLimit(5)
    boolean existsPathByMentor(String from, String to, @PathDirection Direction direction);
}
""")
        moviePaths = buildRepository('test.MoviePathRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestOracleRepository
@PropertyGraph(value = "movies", language = PropertyGraph.QueryLanguage.PGQL)
interface MoviePathRepository extends GenericRepository<Movie, String> {
    long countRelationshipsByDirectors();
    boolean existsRelationshipByDirectorsBetweenTitleAndName(String title, String name);
    List<GraphRelationship<Person, Movie>> findRelationshipsByDirectors(String title, String director);
    long countRelationshipsByDirectors(@PathDirection Direction direction);
}
""")
        pgqlPeople = buildRepository('test.PgqlPersonRepository', """
$MOVIES

@TestOracleRepository
@PropertyGraph(value = "people", language = PropertyGraph.QueryLanguage.PGQL)
interface PgqlPersonRepository extends GenericRepository<Person, String> {
    List<Person> findByMentorName(String name);
    List<Person> findByMentor(Person mentor);
    Optional<Person> findByName(String name);
    Optional<Person> findById(String id);
}
""")
        sqlPeople = buildRepository('test.SqlPersonRepository', """
$MOVIES

@TestOracleRepository
@PropertyGraph(value = "people", language = PropertyGraph.QueryLanguage.SQL_PGQ)
interface SqlPersonRepository extends GenericRepository<Person, String> {
    List<Person> findByMentorName(String name);
    List<Person> findByMentor(Person mentor);
    Optional<Person> findByName(String name);
    Optional<Person> findById(String id);
}
""")
    }

    @Shared
    BeanDefinition<?> pgqlPeople

    @Shared
    BeanDefinition<?> sqlPeople

    @Unroll
    void "test PGQL for #method"() {
        expect:
        getQuery(findMethod(pgql, method, arity)) == query

        where:
        method                                     | arity | query
        'findById'                                 | 1     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.title = ?)"
        'findAll'                                  | 0     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies"
        'count'                                    | 0     | 'SELECT COUNT(*) AS "result" FROM MATCH (n:Movie) ON movies'
        'findByReleasedGreaterThanOrderByReleased' | 1     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.released > ?) ORDER BY n.released ASC"
        'findByTitleAndReleased'                   | 2     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.title = ? AND n.released = ?)"
        'findByReleasedBetween'                    | 2     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE ((n.released >= ? AND n.released <= ?))"
        'findByReleasedIn'                         | 1     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.released IN (?))"
        'findByReleasedNotIn'                      | 1     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (NOT (n.released IN (?)))"
        'findByDescriptionIsNull'                  | 0     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.tagline IS NULL)"
        'findByTitleOrDescription'                 | 2     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.title = ? OR n.tagline = ?)"
        'findByTitleRegex'                         | 1     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (JAVA_REGEXP_LIKE(n.title, ?))"
        'countByReleasedLessThan'                  | 1     | 'SELECT COUNT(*) AS "result" FROM MATCH (n:Movie) ON movies WHERE (n.released < ?)'
        'existsByTitle'                            | 1     | 'SELECT TRUE AS "result" FROM MATCH (n:Movie) ON movies WHERE (n.title = ?) LIMIT 1'
        'findByDirectorsName'                      | 1     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (a0.name = ?)"
        'findByDirectorsNameOrderByDirectorsBorn'  | 1     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (a0.name = ?) ORDER BY a0.born ASC"
        'findByDirectorsIsEmpty'                   | 0     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (a0.id IS NULL)"
        'findByRolesIsNotEmpty'                    | 0     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (ar0.id IS NOT NULL)"
        'findByRolesPersonName'                    | 1     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (a0.name = ?)"
        'findTitleByReleasedGreaterThan'           | 1     | 'SELECT n.title AS "title" FROM MATCH (n:Movie) ON movies WHERE (n.released > ?)'
        'findMaxReleased'                          | 0     | 'SELECT MAX(n.released) AS "result" FROM MATCH (n:Movie) ON movies'
        'findByReleasedLessThan'                   | 1     | 'SELECT n.title AS "title", n.released AS "released" FROM MATCH (n:Movie) ON movies WHERE (n.released < ?)'
        'findTop3OrderByReleasedDesc'              | 0     | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies ORDER BY n.released DESC LIMIT 3"
        'updateReleased'                           | 2     | 'UPDATE "MOVIE" SET "RELEASED"=? WHERE ("TITLE" = ?)'
        'deleteByReleasedLessThan'                 | 1     | 'DELETE  FROM "MOVIE"  WHERE ("RELEASED" < ?)'
        'deleteById'                               | 1     | 'DELETE  FROM "MOVIE"  WHERE ("TITLE" = ?)'
    }

    @Unroll
    void "test SQL/PGQ for #method"() {
        expect:
        getQuery(findMethod(sql, method, arity)) == query

        where:
        method                                     | arity | query
        'findById'                                 | 1     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title = ?) COLUMNS ($COLUMNS))"
        'findAll'                                  | 0     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) COLUMNS ($COLUMNS))"
        'count'                                    | 0     | 'SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) COLUMNS (n.title AS "c0"))'
        'findByReleasedGreaterThanOrderByReleased' | 1     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released > ?) COLUMNS ($COLUMNS)) ORDER BY \"n__released\" ASC"
        'findByTitleAndReleased'                   | 2     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title = ? AND n.released = ?) COLUMNS ($COLUMNS))"
        'findByReleasedBetween'                    | 2     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE ((n.released >= ? AND n.released <= ?)) COLUMNS ($COLUMNS))"
        'findByReleasedIn'                         | 1     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released IN (?)) COLUMNS ($COLUMNS))"
        'findByReleasedNotIn'                      | 1     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (NOT (n.released IN (?))) COLUMNS ($COLUMNS))"
        'findByDescriptionIsNull'                  | 0     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.tagline IS NULL) COLUMNS ($COLUMNS))"
        'findByTitleOrDescription'                 | 2     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title = ? OR n.tagline = ?) COLUMNS ($COLUMNS))"
        'findByTitleStartsWithIgnoreCase'          | 1     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (LOWER(n.title) LIKE LOWER(?) || '%') COLUMNS ($COLUMNS))"
        'findByTitleContains'                      | 1     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title LIKE '%' || ? || '%') COLUMNS ($COLUMNS))"
        'findByTitleEndsWith'                      | 1     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title LIKE '%' || ?) COLUMNS ($COLUMNS))"
        'countByReleasedLessThan'                  | 1     | 'SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released < ?) COLUMNS (n.title AS "c0"))'
        'existsByTitle'                            | 1     | 'SELECT TRUE AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.title = ?) COLUMNS (n.title AS "c0")) FETCH NEXT 1 ROWS ONLY'
        'findByDirectorsName'                      | 1     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (a0.name = ?) COLUMNS ($COLUMNS))"
        'findByDirectorsNameOrderByDirectorsBorn'  | 1     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (a0.name = ?) COLUMNS ($COLUMNS, a0.born AS \"c5\")) ORDER BY \"c5\" ASC"
        'findByDirectorsIsEmpty'                   | 0     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (a0.id IS NULL) COLUMNS ($COLUMNS))"
        'findByRolesIsNotEmpty'                    | 0     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.id IS NOT NULL) COLUMNS ($COLUMNS))"
        'findByRolesRolesIsNotNull'                | 0     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.roles IS NOT NULL) COLUMNS ($COLUMNS))"
        'findTitleByReleasedGreaterThan'           | 1     | 'SELECT "c0" AS "title" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released > ?) COLUMNS (n.title AS "c0"))'
        'findMaxReleased'                          | 0     | 'SELECT MAX("c0") AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) COLUMNS (n.released AS "c0"))'
        'findByReleasedLessThan'                   | 1     | 'SELECT "c0" AS "title", "c1" AS "released" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released < ?) COLUMNS (n.title AS "c0", n.released AS "c1"))'
        'findTop3OrderByReleasedDesc'              | 0     | "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) COLUMNS ($COLUMNS)) ORDER BY \"n__released\" DESC FETCH NEXT 3 ROWS ONLY"
        'updateReleased'                           | 2     | 'UPDATE "MOVIE" SET "RELEASED"=? WHERE ("TITLE" = ?)'
        'deleteByReleasedLessThan'                 | 1     | 'DELETE  FROM "MOVIE"  WHERE ("RELEASED" < ?)'
        'deleteById'                               | 1     | 'DELETE  FROM "MOVIE"  WHERE ("TITLE" = ?)'
    }

    void "test the count query of a pageable finder"() {
        given:
        def pgqlPage = findMethod(pgql, 'findByReleasedGreaterThan', 2)
        def sqlPage = findMethod(sql, 'findByReleasedGreaterThan', 2)

        expect:
        getQuery(pgqlPage) == "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.released > ?)"
        pgqlPage.annotationMetadata.stringValue(Query, 'countQuery').get() == 'SELECT COUNT(*) AS "result" FROM MATCH (n:Movie) ON movies WHERE (n.released > ?)'
        getQuery(sqlPage) == "SELECT * FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released > ?) COLUMNS ($COLUMNS))"
        sqlPage.annotationMetadata.stringValue(Query, 'countQuery').get() == 'SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie) WHERE (n.released > ?) COLUMNS (n.title AS "c0"))'
    }

    @Unroll
    void "test PGQL person query for #method"() {
        expect: "the first label of a node with several, and the identifier property for a generated identifier"
        getQuery(findMethod(pgqlPeople, method, 1)) == query

        where:
        method               | query
        'findById'           | "SELECT $PERSON_COLUMNS FROM MATCH (n:Person) ON people WHERE (n.id = ?)"
        'findByName'         | "SELECT $PERSON_COLUMNS FROM MATCH (n:Person) ON people WHERE (n.name = ?)"
        'findByMentorName'   | "SELECT DISTINCT $PERSON_COLUMNS FROM MATCH (n:Person) ON people, MATCH (n) -[:FOLLOWS]-> (a0:Person) ON people WHERE (a0.name = ?)"
        'findByMentor'       | "SELECT DISTINCT $PERSON_COLUMNS FROM MATCH (n:Person) ON people, MATCH (n) -[:FOLLOWS]-> (a0:Person) ON people WHERE (a0.id = ?)"
    }

    @Unroll
    void "test SQL/PGQ person query for #method"() {
        expect:
        getQuery(findMethod(sqlPeople, method, 1)) == query

        where:
        method               | query
        'findById'           | "SELECT * FROM GRAPH_TABLE (people MATCH (n IS Person) WHERE (n.id = ?) COLUMNS ($PERSON_COLUMNS))"
        'findByName'         | "SELECT * FROM GRAPH_TABLE (people MATCH (n IS Person) WHERE (n.name = ?) COLUMNS ($PERSON_COLUMNS))"
        'findByMentorName'   | "SELECT DISTINCT * FROM GRAPH_TABLE (people MATCH (n IS Person), (n) -[IS FOLLOWS]-> (a0 IS Person) WHERE (a0.name = ?) COLUMNS ($PERSON_COLUMNS))"
        'findByMentor'       | "SELECT DISTINCT * FROM GRAPH_TABLE (people MATCH (n IS Person), (n) -[IS FOLLOWS]-> (a0 IS Person) WHERE (a0.id = ?) COLUMNS ($PERSON_COLUMNS))"
    }

    void "test writes are compiled to SQL for the vertex table"() {
        when:
        def repository = buildRepository('test.AccountRepository', '''
@Node(value = "accounts", labels = "Account")
class Account {
    @Id
    private Long id;
    private String name;
    @Relationship("TRANSFERS")
    private List<Account> transfers = new ArrayList<>();
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Account> getTransfers() { return transfers; }
    public void setTransfers(List<Account> transfers) { this.transfers = transfers; }
}

@TestOracleRepository
@PropertyGraph(value = "bank_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ)
interface AccountRepository extends CrudRepository<Account, Long> {
}
''')
        def queries = repository.executableMethods.collectEntries { m ->
            [(m.methodName + m.argumentTypes*.simpleName): m.annotationMetadata.stringValue(Query).orElse(null)]
        }

        then:
        queries['save[Account]'] == 'INSERT INTO "ACCOUNTS" ("NAME","ID") VALUES (?,?)'
        queries['update[Account]'] == 'UPDATE "ACCOUNTS" SET "NAME"=? WHERE ("ID" = ?)'
        queries['deleteById[Long]'] == 'DELETE  FROM "ACCOUNTS"  WHERE ("ID" = ?)'
        queries['findById[Long]'].contains('GRAPH_TABLE (bank_graph MATCH (n IS Account) WHERE (n.id = ?)')
    }

    void "test explicit queries take positional parameters"() {
        given:
        def method = findMethod(pgql, 'titles')

        expect:
        method.annotationMetadata.stringValue(Query, 'rawQuery').get() == 'SELECT m.title AS title FROM MATCH (m:Movie) ON movies WHERE m.released > ? AND m.released < ? + 10'
        // Bound by position, as SQL repositories are: the runtime names them p0 and p1
        getParameterNames(method) == ['', '']
    }

    void "test a dollar inside a name, a SQL comment and a backslash in a literal are not parameters"() {
        given:
        def method = findMethod(sql, 'sessions')

        expect:
        def rawQuery = method.annotationMetadata.stringValue(Query, 'rawQuery').get()
        rawQuery.startsWith("SELECT COUNT(*) AS result FROM v\$session WHERE sid = ? AND path <> 'C:\\")
        rawQuery.endsWith("' -- see \$ticket")

        and: "only the sid is bound"
        method.annotationMetadata.getAnnotation(io.micronaut.data.intercept.annotation.DataMethod)
            .getAnnotations(io.micronaut.data.intercept.annotation.DataMethod.META_MEMBER_PARAMETERS, io.micronaut.data.intercept.annotation.DataMethodQueryParameter)
            *.intValue(io.micronaut.data.intercept.annotation.DataMethodQueryParameter.META_MEMBER_PARAMETER_INDEX)*.asInt == [0]
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

        expect:
        getQuery(findMethod(paths, 'findShortestPathByMentor')) ==
            "SELECT $steps FROM MATCH ANY SHORTEST (s:Person) -[x:FOLLOWS]->{1,5} (t:Person) ON people ONE ROW PER STEP (v1, x, v2) WHERE s.id = ? AND t.id = ? ORDER BY MATCHNUM(v1), ELEMENT_NUMBER(x)"
        getQuery(findMethod(paths, 'findAllShortestPathsByMentor')).contains("FROM MATCH ALL SHORTEST (s:Person) -[x:FOLLOWS]->{1,5} (t:Person) ON people")
        getQuery(findMethod(paths, 'existsPathByMentor', 2)) ==
            'SELECT 1 AS "result" FROM MATCH ANY (s:Person) -[:FOLLOWS]->{1,5} (t:Person) ON people WHERE s.id = ? AND t.id = ? LIMIT 1'
        getQuery(findMethod(paths, 'countPaths')) ==
            'SELECT COUNT(*) AS "result" FROM MATCH ALL (s:Person) -[]-{1,5} (t:Person) ON people WHERE s.id = ? AND t.id = ?'
        getQuery(findMethod(paths, 'findRelationshipsByMentor')) ==
            "SELECT ${String.format(person, 's')}, ${String.format(person, 's').replace('s.', 't.').replace('"s__', '"t__')}, 'FOLLOWS' AS \"r__TYPE_\" FROM MATCH (s:Person) -[r:FOLLOWS]-> (t:Person) ON people WHERE s.id = ? AND t.id = ?"
    }

    void "test PGQL path finders with a direction argument"() {
        expect:
        getQuery(findMethod(paths, 'existsPathByMentor', 3)) ==
            'SELECT 1 AS "result" FROM MATCH ANY (s:Person) $__pathLeft[:FOLLOWS]$__pathRight{1,5} (t:Person) ON people WHERE s.id = ? AND t.id = ? LIMIT 1'
        getQuery(findMethod(moviePaths, 'countRelationshipsByDirectors', 1)) ==
            'SELECT COUNT(*) AS "result" FROM MATCH (s:Movie) $__pathLeft[r:DIRECTED]$__pathRight (t:Person) ON movies'
    }

    void "test PGQL relationship finders between movies and people"() {
        given:
        def movie = 's.title AS "s__ID_", s.title AS "s__title", \'Movie\' AS "s__LABEL_", s.tagline AS "s__tagline", s.released AS "s__released"'
        def person = 't.id AS "t__ID_", t.id AS "t__id", \'Person\' AS "t__LABEL_", t.name AS "t__name", t.born AS "t__born"'

        expect:
        getQuery(findMethod(moviePaths, 'countRelationshipsByDirectors', 0)) ==
            'SELECT COUNT(*) AS "result" FROM MATCH (s:Movie) <-[r:DIRECTED]- (t:Person) ON movies'
        getQuery(findMethod(moviePaths, 'existsRelationshipByDirectorsBetweenTitleAndName')) ==
            'SELECT 1 AS "result" FROM MATCH (s:Movie) <-[r:DIRECTED]- (t:Person) ON movies WHERE s.title = ? AND t.name = ? LIMIT 1'
        getQuery(findMethod(moviePaths, 'findRelationshipsByDirectors')) ==
            "SELECT $movie, $person, 'DIRECTED' AS \"r__TYPE_\" FROM MATCH (s:Movie) <-[r:DIRECTED]- (t:Person) ON movies WHERE s.title = ? AND t.id = ?"
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

        'LIKE in PGQL'         | ', language = PropertyGraph.QueryLanguage.PGQL'                                                    | 'List<Movie> findByTitleStartsWith(String prefix);'                 | 'PGQL has no LIKE'
        'fetch joins'          | ''                                                    | '@Join("directors") List<Movie> findByReleased(Integer released);' | 'not supported for Oracle property graphs'
        'paths in SQL/PGQ'     | ', language = PropertyGraph.QueryLanguage.SQL_PGQ'    | '@PathLimit(3) Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'use @PropertyGraph(language = PGQL)'
        'unbounded paths'      | ''                                                    | 'Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'need a maximum depth'
        'relationship entities' | ''                                                   | 'List<Role> findRelationshipsByRoles(String title, String person);'  | 'return GraphRelationship, not the relationship entity'
        'paths to another entity' | ', language = PropertyGraph.QueryLanguage.PGQL'    | '@PathLimit(3) boolean existsPathToPerson(String title, String person);' | 'the end node cannot be a'
        'array contains'       | ''                                                    | 'List<Movie> findByRolesRolesArrayContains(String role);'          | 'ArrayContains is not supported'
    }

    void "test schemaless properties are read from a JSON column"() {
        when:
        def repository = buildRepository('test.PetRepository', """
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
@PropertyGraph(value = "pets", language = PropertyGraph.QueryLanguage.SQL_PGQ)
interface PetRepository extends GenericRepository<Pet, String> {
    List<Pet> findByName(String name);
}
""")

        then:
        getQuery(findMethod(repository, 'findByName')) == '''SELECT * FROM GRAPH_TABLE (pets MATCH (n IS Pet) WHERE (n.name = ?) COLUMNS (n.name AS "n__ID_", n.name AS "n__name", 'Pet' AS "n__LABEL_", n.attributes AS "n__DYN_"))'''
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
