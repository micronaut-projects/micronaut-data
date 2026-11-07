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
        'findByRolesIsNotNull'                     | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (ar0.id IS NOT NULL)"
        'findByRoles'                              | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (ar0.id = ?)"
        'findByRolesPerson'                        | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[ar0:ACTED_IN]- (a0:Person) ON movies WHERE (a0.id = ?)"
        'findByDirectorsIsNotNull'                 | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (a0.id IS NOT NULL)"
        'findByTitle'                              | "SELECT DISTINCT $COLUMNS FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (n.title = ?)"
        'countByTitle'                             | 'SELECT COUNT(DISTINCT n.title) AS "result" FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (n.title = ?)'
        'findTitleByReleased'                      | 'SELECT n.title AS "title" FROM MATCH (n:Movie) ON movies, MATCH (n) <-[:DIRECTED]- (a0:Person) ON movies WHERE (n.released = ?)'
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
@PropertyGraph("movies")
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
        getQuery(findMethod(paths, 'existsPathByMentor')) ==
            'SELECT 1 AS "result" FROM GRAPH_TABLE (people MATCH (s IS Person) -[IS FOLLOWS]->{1,5} (t IS Person) WHERE s.id = ? AND t.id = ? AND s.id <> t.id COLUMNS (s.id AS "c0")) FETCH NEXT 1 ROWS ONLY'

        and: "all paths from a node to itself are its cycles"
        getQuery(findMethod(paths, 'countPaths')) ==
            'SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (people MATCH (s IS Person) -[]-{1,5} (t IS Person) WHERE s.id = ? AND t.id = ? COLUMNS (s.id AS "c0"))'

        and: "a @PathLimit argument is rendered by the runtime in place of the token, over the depth of the annotation"
        getQuery(findMethod(paths, 'countPathsByMentor')) ==
            'SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (people MATCH (s IS Person) -[IS FOLLOWS]->{1,$__pathLimit} (t IS Person) WHERE s.id = ? AND t.id = ? COLUMNS (s.id AS "c0"))'
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
        getQuery(findMethod(repository, 'findRelationshipsByFollowers')).endsWith(
            "FROM MATCH (t:Follower) <-[r:FOLLOWS]- (s:Follower) ON people WHERE t.id = ? AND s.id = ?")

        and: "an undirected edge with both ends known is matched either way"
        getQuery(findMethod(repository, 'findRelationshipsByKnown')).endsWith(
            "FROM MATCH (s:Follower) -[r:KNOWS]- (t:Follower) ON people WHERE s.id = ? AND t.id = ?")

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
        'PGQL paths'           | ''                                                    | '@PathLimit(3) Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'PGQL path queries cannot bind parameters'
        'PGQL path counts'     | ''                                                    | '@PathLimit(3) long countPaths(String from, String to);'            | 'PGQL path queries cannot bind parameters'
        'PGQL all shortest'    | ''                                                    | '@PathLimit(3) List<GraphPath<Movie>> findAllShortestPaths(String from, String to);' | 'ALL SHORTEST is not supported by PGQL'
        'IsEmpty on a relationship' | ''                                               | 'List<Movie> findByDirectorsIsEmpty();'                             | 'Expected a string expression'
        'IsEmpty on a relationship identifier' | ''                                    | 'List<Movie> findByDirectorsIdIsEmpty();'                           | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a relationship' | ''                                                | 'List<Movie> findByDirectorsIsNull();'                              | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a relationship entity' | ', language = PropertyGraph.QueryLanguage.SQL_PGQ' | 'List<Movie> findByRolesIsNull();'                       | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'IsNull on a nested to-one relationship' | ''                                  | 'List<Movie> findByDirectorsMentorIsNull();'                        | 'Testing that a relationship is absent is not supported for Oracle property graphs'
        'sort on a relationship' | ''                                                  | 'List<Movie> findByReleasedOrderByDirectorsBorn(Integer released);' | 'Sorting on a relationship is not supported for Oracle property graphs'
        'sort on a bound relationship' | ', language = PropertyGraph.QueryLanguage.SQL_PGQ' | 'List<Movie> findByDirectorsNameOrderByDirectorsBorn(String name);' | 'Sorting on a relationship is not supported for Oracle property graphs'
        'LIKE in PGQL'         | ''                                                    | 'List<Movie> findByTitleStartsWith(String prefix);'                 | 'PGQL has no LIKE'
        'fetch joins'          | ''                                                    | '@Join("directors") List<Movie> findByReleased(Integer released);' | 'not supported for Oracle property graphs'
        'paths in SQL/PGQ'     | ', language = PropertyGraph.QueryLanguage.SQL_PGQ'    | '@PathLimit(3) Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'Returning paths is not supported by the SQL/PGQ dialect'
        'unbounded paths'      | ''                                                    | 'Optional<GraphPath<Movie>> findShortestPath(String from, String to);' | 'need a maximum depth'
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
