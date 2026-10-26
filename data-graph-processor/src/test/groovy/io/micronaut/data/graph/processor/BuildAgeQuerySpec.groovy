package io.micronaut.data.graph.processor

import io.micronaut.data.annotation.Query
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

    @Shared
    BeanDefinition<?> friends

    @Shared
    BeanDefinition<?> accounts

    void setupSpec() {
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
        'findShortestPathByFriends' | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[:FRIENDS*1..$__pathLimit]->(e) RETURN p ORDER BY length(p) LIMIT 1'
        'findAllShortestPaths'      | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[*1..3]-(e) WITH s, e, min(length(p)) AS shortest MATCH p = (s)-[*1..3]-(e) WHERE length(p) = shortest RETURN p'
        'existsPath'                | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[*1..3]-(e) RETURN true AS result LIMIT 1'
        'findByNameStartsWith'      | 'MATCH (n:Friend) WHERE n.name STARTS WITH $p0 RETURN n'
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
}
