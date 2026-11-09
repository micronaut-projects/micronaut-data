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
    List<Movie> findByRolesIsNotNull();
    List<Movie> findByRoles(Role role);
    List<Movie> findByRolesPerson(Person person);
    List<Movie> findByDirectorsIsNotNull();
    @Join(value = "directors", type = Join.Type.INNER)
    List<Movie> findByTitle(String title);
    @Join(value = "directors", type = Join.Type.INNER)
    long countByTitle(String title);
    @Join(value = "directors", type = Join.Type.INNER)
    List<String> findTitleByReleased(Integer released);
    @Join(value = "directors", type = Join.Type.INNER)
    Page<Movie> findByReleasedGreaterThanEquals(Integer released, Pageable pageable);
    @Join(value = "directors", type = Join.Type.LEFT)
    Page<Movie> findByReleasedLessThanEquals(Integer released, Pageable pageable);
    List<String> findTitleByReleasedGreaterThan(int released);
    Integer findMaxReleased();
    List<MovieSummary> findByReleasedLessThan(Integer released);
    List<Movie> findByTitleRegex(String pattern);
    Movie findByTitleAndReleased(String title, Integer released);
    List<Movie> findByReleasedBetween(int from, int to);
    List<Movie> findByReleasedNotIn(List<Integer> years);
    List<Movie> findByDescriptionIsNull();
    List<Movie> findByTitleOrDescription(String title, String description);
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
import io.micronaut.data.graph.model.*;

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
    List<Movie> findByTitleEndsWith(String suffix);
    List<Movie> findByReleasedIn(List<Integer> years);
    Movie findByTitleAndReleased(String title, Integer released);
    List<Movie> findByReleasedBetween(int from, int to);
    List<Movie> findByReleasedNotIn(List<Integer> years);
    List<Movie> findByDescriptionIsNull();
    List<Movie> findByTitleOrDescription(String title, String description);
    List<Movie> findByRolesIsNotEmpty();
    void updateReleased(@Id String title, Integer released);
    void deleteByReleasedLessThan(Integer released);
    List<Movie> findTop3OrderByReleasedDesc();
    Page<Movie> findByReleasedGreaterThan(Integer released, Pageable pageable);
    List<Movie> findAll();
    long count();
    void deleteById(String id);
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
@PropertyGraph(value = "people", language = PropertyGraph.QueryLanguage.SQL_PGQ)
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
    @PathLimit(5)
    boolean existsPathByMentor(String from, String to, @PathDirection Direction direction);
    List<GraphRelationship<Person, Person>> findRelationshipsByMentor(String from, String to, @PathDirection Direction direction);
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
    List<GraphRelationship<Person, Movie>> findRelationshipsByDirectors(String title, String director, @PathDirection Direction direction);
    @PathDirection(Direction.UNDIRECTED)
    boolean existsRelationshipByDirectors();
    boolean existsRelationshipByDirectorsBetweenReleasedAndId(int released, String director);
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
        'findByRolesIsNotEmpty'                    | 0     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (ar0.id IS NOT NULL)"
        'findByRolesPersonName'                    | 1     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (a0.name = ?)"
        'findByRolesIsNotNull'                     | 0     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (ar0.id IS NOT NULL)"
        'findByRoles'                              | 1     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (ar0.id = ?)"
        'findByRolesPerson'                        | 1     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (a0.id = ?)"
        'findByDirectorsIsNotNull'                 | 0     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (a0.id IS NOT NULL)"
        'findByTitle'                              | 1     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (n.title = ?)"
        'countByTitle'                             | 1     | 'SELECT COUNT(DISTINCT n.title) AS "result" FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (n.title = ?)'
        'findTitleByReleased'                      | 1     | 'SELECT n.title AS "title" FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (n.released = ?)'
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
        'findByRolesIsNotEmpty'                    | 0     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.id IS NOT NULL) COLUMNS ($COLUMNS))"
        'findByRolesRolesIsNotNull'                | 0     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.roles IS NOT NULL) COLUMNS ($COLUMNS))"
        'findByRolesIsNotNull'                     | 0     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.id IS NOT NULL) COLUMNS ($COLUMNS))"
        'findByRoles'                              | 1     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (ar0.id = ?) COLUMNS ($COLUMNS))"
        'findByRolesPerson'                        | 1     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[ar0 IS ACTED_IN]- (a0 IS Person) WHERE (a0.id = ?) COLUMNS ($COLUMNS))"
        'findByTitle'                              | 1     | "SELECT DISTINCT * FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (n.title = ?) COLUMNS ($COLUMNS))"
        'countByTitle'                             | 1     | 'SELECT COUNT(DISTINCT "c0") AS "result" FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (n.title = ?) COLUMNS (n.title AS "c0"))'
        'findTitleByReleased'                      | 1     | 'SELECT "c0" AS "title" FROM GRAPH_TABLE (movies MATCH (n IS Movie), (n) <-[IS DIRECTED]- (a0 IS Person) WHERE (n.released = ?) COLUMNS (n.title AS "c0"))'
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

    static final String SINGLE_RELATIONSHIP = '''
@Node(value = "accounts", labels = "Account")
class Account {
    @Id
    private Long id;
    private String name;
    @Relationship("REFERRED_BY")
    private Account referrer;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Account getReferrer() { return referrer; }
    public void setReferrer(Account referrer) { this.referrer = referrer; }
}
'''

    void "test writes leave a single relationship out of the vertex table"() {
        when:
        def repository = buildRepository('test.AccountRepository', SINGLE_RELATIONSHIP + '''
@TestOracleRepository
@PropertyGraph(value = "bank_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ)
interface AccountRepository extends CrudRepository<Account, Long> {
    String updateReturningName(Account account);
    String deleteReturningName(Account account);
}
''')
        def queries = repository.executableMethods.collectEntries { m ->
            [(m.methodName + m.argumentTypes*.simpleName): m.annotationMetadata.stringValue(Query).orElse(null)]
        }

        then:
        queries['save[Account]'] == 'INSERT INTO "ACCOUNTS" ("NAME","ID") VALUES (?,?)'
        queries['update[Account]'] == 'UPDATE "ACCOUNTS" SET "NAME"=? WHERE ("ID" = ?)'
        queries['updateReturningName[Account]'] == 'BEGIN UPDATE "ACCOUNTS" SET "NAME"=? WHERE ("ID" = ?) RETURNING "NAME" INTO ?; END;'
        queries['deleteReturningName[Account]'] == 'BEGIN DELETE  FROM "ACCOUNTS"  WHERE ("ID" = ?) RETURNING "NAME" INTO ?; END;'
    }

    @Unroll
    void "test #method is rejected"() {
        when:
        buildRepository('test.AccountRepository', SINGLE_RELATIONSHIP + """
@TestOracleRepository
@PropertyGraph(value = "bank_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ)
interface AccountRepository extends GenericRepository<Account, Long> {
    $method;
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        method                                    | message
        'void update(@Id Long id, Account referrer)' | 'Cannot update the relationship [referrer]'
        'Account insertReturning(Account account)'   | 'Cannot return the entity [test.Account] from a write'
        'Account updateReturning(Account account)'   | 'Cannot return the entity [test.Account] from a write'
        'Account deleteReturning(Account account)'   | 'Cannot return the entity [test.Account] from a write'
    }

    @Unroll
    void "test the page and its total of #method agree on the join"() {
        given:
        def m = findMethod(pgql, method)

        expect:
        getQuery(m) == query
        m.annotationMetadata.stringValue(Query, 'countQuery').get() == countQuery

        where:
        method                            | query                                                                                                                       | countQuery
        'findByReleasedGreaterThanEquals' | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (n.released >= ?)" | 'SELECT COUNT(DISTINCT n.title) AS "result" FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (n.released >= ?)'
        'findByReleasedLessThanEquals'    | "SELECT $COLUMNS FROM MATCH (n:Movie) ON movies WHERE (n.released <= ?)"                                                    | 'SELECT COUNT(DISTINCT n.title) AS "result" FROM MATCH (n:Movie) ON movies WHERE (n.released <= ?)'
    }

    void "test a relationship entity criterion binds the identifier of what it compares"() {
        expect:
        getParameterProperties(findMethod(pgql, 'findByRoles')) == ['roles']
        getParameterProperties(findMethod(pgql, 'findByRolesPerson')) == ['roles.person']
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
@PropertyGraph(value = "movies", language = PropertyGraph.QueryLanguage.PGQL)
interface CastRepository extends GenericRepository<Cast, String> {
    List<Cast> findByCameosIsNotNull();
}
""")

        expect:
        getQuery(findMethod(repository, 'findByCameosIsNotNull')).endsWith('MATCH (n) <-[ar0:APPEARS_IN]- (a0:Person) ON movies WHERE (a0.id IS NOT NULL)')
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

    void "test SQL/PGQ path finders"() {
        expect: "the shortest kinds exclude the empty path from a node to itself, as the Cypher builder does"
        getQuery(findMethod(paths, 'existsPathByMentor', 2)) ==
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
@PropertyGraph(value = "people", language = PropertyGraph.QueryLanguage.PGQL)
interface FollowerRepository extends GenericRepository<Follower, String> {
    List<GraphRelationship<Follower, Follower>> findRelationshipsByFollowers(String from, String to);
    List<GraphRelationship<Follower, Follower>> findRelationshipsByKnown(String from, String to);
}
""")

        then: "s stays the source of an incoming edge, bound to the far end"
        getQuery(findMethod(repository, 'findRelationshipsByFollowers')).endsWith(
            "FROM MATCH (t:Follower) <-[r:FOLLOWS]- (s:Follower) ON people WHERE t.id = ? AND s.id = ?")

        and: "an undirected edge with both ends known is matched either way"
        getQuery(findMethod(repository, 'findRelationshipsByKnown')).endsWith(
            "FROM MATCH (s:Follower) -[r:KNOWS]- (t:Follower) ON people WHERE s.id = ? AND t.id = ?")

        and: "the rows say which end of an undirected edge is its source"
        getQuery(findMethod(repository, 'findRelationshipsByKnown')).contains('CASE WHEN s IS SOURCE OF r THEN 1 ELSE 0 END AS "r__FORWARD_"')
        !getQuery(findMethod(repository, 'findRelationshipsByFollowers')).contains('r__FORWARD_')
    }

    void "test path finders with a direction argument"() {
        expect:
        getQuery(findMethod(paths, 'existsPathByMentor', 3)) ==
            'SELECT 1 AS "result" FROM GRAPH_TABLE (people MATCH (s IS Person) $__pathLeft[IS FOLLOWS]$__pathRight{1,5} (t IS Person) WHERE s.id = ? AND t.id = ? AND s.id <> t.id COLUMNS (s.id AS "c0")) FETCH NEXT 1 ROWS ONLY'
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
        getQuery(findMethod(moviePaths, 'findRelationshipsByDirectors', 2)) ==
            "SELECT ${person.replace('t.', 's.').replace('"t__', '"s__')}, ${movie.replace('s.', 't.').replace('"s__', '"t__')}, 'DIRECTED' AS \"r__TYPE_\" FROM MATCH (t:Movie) <-[r:DIRECTED]- (s:Person) ON movies WHERE t.title = ? AND s.id = ?"

        and: "a direction argument leaves s at the first argument, and the rows ask the edge for its source"
        getQuery(findMethod(moviePaths, 'findRelationshipsByDirectors', 3)) ==
            "SELECT $movie, $person, 'DIRECTED' AS \"r__TYPE_\", CASE WHEN s IS SOURCE OF r THEN 1 ELSE 0 END AS \"r__FORWARD_\" FROM MATCH (s:Movie) \$__pathLeft[r:DIRECTED]\$__pathRight (t:Person) ON movies WHERE s.title = ? AND t.id = ?"
        getQuery(findMethod(paths, 'findRelationshipsByMentor', 3)).contains('CASE WHEN s IS SOURCE OF r THEN 1 ELSE 0 END AS "r__FORWARD_"')

        and: "an undirected finder between different entities without endpoints stays undirected"
        getQuery(findMethod(moviePaths, 'existsRelationshipByDirectors', 0)) ==
            'SELECT 1 AS "result" FROM MATCH (s:Movie) -[r:DIRECTED]- (t:Person) ON movies LIMIT 1'

        and: "the end node is matched by its own identity"
        getQuery(findMethod(moviePaths, 'existsRelationshipByDirectorsBetweenReleasedAndId', 2)) ==
            'SELECT 1 AS "result" FROM MATCH (s:Movie) <-[r:DIRECTED]- (t:Person) ON movies WHERE s.released = ? AND t.id = ? LIMIT 1'
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
        'PGQL paths'           | ', language = PropertyGraph.QueryLanguage.PGQL'        | '@PathLimit(3) Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'PGQL path queries cannot bind parameters'
        'PGQL path counts'     | ', language = PropertyGraph.QueryLanguage.PGQL'        | '@PathLimit(3) long countPaths(String from, String to);'            | 'PGQL path queries cannot bind parameters'
        'PGQL all shortest'    | ', language = PropertyGraph.QueryLanguage.PGQL'        | '@PathLimit(3) List<GraphPath<Movie>> findAllShortestPaths(String from, String to);' | 'ALL SHORTEST is not supported by PGQL'
        'IsEmpty on a relationship' | ''                                               | 'List<Movie> findByDirectorsIsEmpty();'                             | 'using @Query instead'
        'IsEmpty on a relationship in PGQL' | ', language = PropertyGraph.QueryLanguage.PGQL' | 'List<Movie> findByDirectorsIsEmpty();'                        | 'using @Query instead'
        'IsEmpty on a relationship identifier' | ''                                    | 'List<Movie> findByDirectorsIdIsEmpty();'                           | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a relationship' | ''                                                | 'List<Movie> findByDirectorsIsNull();'                              | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a relationship entity' | ', language = PropertyGraph.QueryLanguage.SQL_PGQ' | 'List<Movie> findByRolesIsNull();'                       | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a nested to-one relationship' | ''                                  | 'List<Movie> findByDirectorsMentorIsNull();'                        | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'sort on a relationship' | ''                                                  | 'List<Movie> findByReleasedOrderByDirectorsBorn(Integer released);' | 'Sorting on a relationship is not supported for Oracle property graphs'
        'sort on a bound relationship' | ', language = PropertyGraph.QueryLanguage.SQL_PGQ' | 'List<Movie> findByDirectorsNameOrderByDirectorsBorn(String name);' | 'Sorting on a relationship is not supported for Oracle property graphs'
        'LIKE in PGQL'         | ', language = PropertyGraph.QueryLanguage.PGQL'                                                    | 'List<Movie> findByTitleStartsWith(String prefix);'                 | 'PGQL has no LIKE'
        'fetch joins'          | ''                                                    | '@Join("directors") List<Movie> findByReleased(Integer released);' | 'not supported for Oracle property graphs'
        'shortest paths in SQL/PGQ' | ', language = PropertyGraph.QueryLanguage.SQL_PGQ' | '@PathLimit(3) Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'Shortest paths are not supported by the SQL/PGQ dialect'
        'any-type paths in SQL/PGQ' | ', language = PropertyGraph.QueryLanguage.SQL_PGQ' | '@PathLimit(3) List<GraphPath<Movie>> findAllPaths(String from, String to);' | 'Paths over any relationship type are not supported by the SQL/PGQ dialect'
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

    @Unroll
    void "a non-blocking repository rejects a method returning #description"() {
        when:
        buildRepository('test.BlockingRepository', """
$MOVIES

@TestNonBlockingRepository(graph = "movies")
interface BlockingRepository extends $parent {
    $method
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("so the method [$name] must return a reactive or asynchronous type")

        where:
        description     | parent                               | method                                  | name
        'a list'        | 'GenericRepository<Movie, String>'   | 'List<Movie> findByReleased(int year);' | 'findByReleased'
        'an entity'     | 'GenericRepository<Movie, String>'   | 'Movie findById(String id);'            | 'findById'
        'a number'      | 'GenericRepository<Movie, String>'   | 'long countByReleased(int year);'       | 'countByReleased'
        'an optional'   | 'GenericRepository<Movie, String>'   | 'Optional<Movie> find(String title);'   | 'find'
        'nothing'       | 'GenericRepository<Movie, String>'   | 'void deleteById(String id);'           | 'deleteById'
        'an entity too' | 'CrudRepository<Movie, String>'      | ''                                      | 'findById'
    }

    void "a non-blocking repository accepts reactive and asynchronous methods"() {
        when:
        def definition = buildRepository('test.ReactiveRepository', """
$MOVIES

@TestNonBlockingRepository(graph = "movies")
interface ReactiveRepository extends io.micronaut.data.repository.reactive.ReactorCrudRepository<Movie, String> {
    reactor.core.publisher.Flux<Movie> findByReleased(int year);
    org.reactivestreams.Publisher<Movie> findByTitle(String title);
    java.util.concurrent.CompletableFuture<Long> countByReleased(int year);
    default String name() {
        return "movies";
    }
}
""")

        then:
        definition != null
    }

    void "test two relationships sharing an edge table fail the build"() {
        when:
        buildRepository('test.FollowerRepository', """
@Node
class Account {
    @Id
    private String handle;
    @Relationship(type = "SUBSCRIBES")
    private List<Channel> subscriptions = new ArrayList<>();
    @Relationship(type = "SUBSCRIBES", direction = Direction.INCOMING)
    private List<Channel> subscribers = new ArrayList<>();

    public String getHandle() { return handle; }
    public void setHandle(String handle) { this.handle = handle; }
    public List<Channel> getSubscriptions() { return subscriptions; }
    public void setSubscriptions(List<Channel> subscriptions) { this.subscriptions = subscriptions; }
    public List<Channel> getSubscribers() { return subscribers; }
    public void setSubscribers(List<Channel> subscribers) { this.subscribers = subscribers; }
}

@Node
class Channel {
    @Id
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@TestOracleRepository
@PropertyGraph("accounts")
interface FollowerRepository extends GenericRepository<Account, String> {
    List<Account> findByHandle(String handle);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Relationships [test.Account.subscriptions] and [test.Account.subscribers] both map to the edge table "ACCOUNT_SUBSCRIBES", which can only hold one relationship type between one pair of nodes: use a relationship entity for each of them')
    }

    static final String FILMS = '''
@Node
class Film {
    @Id
    private String title;
    @Relationship(type = "STARRED_IN", direction = Direction.INCOMING)
    private List<Credit> credits = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<Credit> getCredits() { return credits; }
    public void setCredits(List<Credit> credits) { this.credits = credits; }
}

@RelationshipProperties
class Credit {
    @RelationshipId
    private String id;
    private String role;
    @TargetNode
    private Star star;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Star getStar() { return star; }
    public void setStar(Star star) { this.star = star; }
}
'''

    void "test a relationship entity on both sides of a relationship fails the build"() {
        when:
        buildRepository('test.FilmRepository', """
$FILMS

@RelationshipProperties
class Appearance {
    @RelationshipId
    private String id;
    @TargetNode
    private Film film;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Film getFilm() { return film; }
    public void setFilm(Film film) { this.film = film; }
}

@Node
class Star {
    @Id
    private String name;
    @Relationship(type = "STARRED_IN")
    private List<Appearance> appearances = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Appearance> getAppearances() { return appearances; }
    public void setAppearances(List<Appearance> appearances) { this.appearances = appearances; }
}

@TestOracleRepository
@PropertyGraph("films")
interface FilmRepository extends GenericRepository<Film, String> {
    List<Film> findByTitle(String title);
}
""")

        then: "the conflict is found on a node reached through a relationship entity"
        def e = thrown(RuntimeException)
        e.message.contains('Relationships [test.Star.appearances] and [test.Film.credits] are the two sides of one relationship with the edge tables "APPEARANCE" and "CREDIT": declare the relationship entity on one side only')
    }

    void "test a relationship entity on one side of a relationship compiles"() {
        when:
        def repository = buildRepository('test.FilmRepository', """
$FILMS

@Node
class Star {
    @Id
    private String name;
    @Relationship(type = "STARRED_IN")
    private List<Film> films = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Film> getFilms() { return films; }
    public void setFilms(List<Film> films) { this.films = films; }
}

@TestOracleRepository
@PropertyGraph("films")
interface FilmRepository extends GenericRepository<Film, String> {
    List<Film> findByCreditsStarName(String name);
}
""")

        then:
        getQuery(findMethod(repository, 'findByCreditsStarName')).contains('STARRED_IN')
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
