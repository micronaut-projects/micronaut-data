package io.micronaut.data.graph.processor

import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

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
        getQuery(findMethod(friends, method)) == query

        where:
        method                      | query
        'findShortestPathByFriends' | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[:FRIENDS*1..$__pathLimit]->(e) RETURN p ORDER BY length(p) LIMIT 1'
        'findAllShortestPaths'      | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[*1..3]-(e) WITH s, e, min(length(p)) AS shortest MATCH p = (s)-[*1..3]-(e) WHERE length(p) = shortest RETURN p'
        'existsPath'                | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = (s)-[*1..3]-(e) RETURN true AS result LIMIT 1'
        'findByNameStartsWith'      | 'MATCH (n:Friend) WHERE n.name STARTS WITH $p0 RETURN n'
    }

    void "test database assigned identifiers are graph ids"() {
        expect:
        getQuery(findMethod(accounts, 'findById')) == 'MATCH (n:Account) WHERE id(n) = $p0 RETURN n'
        getQuery(findMethod(accounts, 'findByOwner')) == 'MATCH (n:Account) WHERE n.owner = $p0 RETURN n'
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

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> definition, String name) {
        def method = definition.executableMethods.find { it.methodName == name }
        assert method != null: "No method $name"
        return method
    }
}
