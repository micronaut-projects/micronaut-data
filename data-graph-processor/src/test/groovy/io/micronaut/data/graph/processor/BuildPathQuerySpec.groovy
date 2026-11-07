package io.micronaut.data.graph.processor

import io.micronaut.data.annotation.Query
import io.micronaut.data.graph.model.query.PathLimitParameter
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

class BuildPathQuerySpec extends AbstractGraphDataSpec {

    static final String FRIENDS = '''
@Node
class Friend {
    @Id
    private String name;
    @Relationship("FRIENDS")
    private List<Friend> friends = new ArrayList<>();
    @Relationship(type = "WORKS_WITH", direction = Direction.UNDIRECTED)
    private List<Friend> colleagues = new ArrayList<>();
    @Relationship("FRIENDS_OF_FRIENDS")
    private List<Friend> acquaintances = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Friend> getFriends() { return friends; }
    public void setFriends(List<Friend> friends) { this.friends = friends; }
    public List<Friend> getColleagues() { return colleagues; }
    public void setColleagues(List<Friend> colleagues) { this.colleagues = colleagues; }
    public List<Friend> getAcquaintances() { return acquaintances; }
    public void setAcquaintances(List<Friend> acquaintances) { this.acquaintances = acquaintances; }
}
'''

    static final String TENANT_MEMBER = '''
@Node
class Member {
    @Id
    private String name;
    @io.micronaut.data.annotation.TenantId
    private String tenant;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }
}
'''

    @Shared
    BeanDefinition<?> repository

    void setupSpec() {
        repository = buildRepository('test.FriendRepository', """
import io.micronaut.data.graph.model.*;

$FRIENDS

@TestGraphRepository
interface FriendRepository extends CrudRepository<Friend, String> {
    Optional<GraphPath<Friend>> findShortestPath(Friend from, Friend to);
    Optional<GraphPath<Friend>> findShortestPathByFriends(String from, String to, @PathLimit int maxDepth);
    @PathLimit(4)
    Optional<GraphPath<Friend>> findPath(String from, String to);
    List<GraphPath<Friend>> findAllShortestPaths(String from, String to);
    List<GraphPath<Friend>> findAllPathsByFriendsOrColleagues(String from, String to, @PathLimit int maxDepth);
    List<GraphPath<Friend>> findAllPathsByAcquaintancesOrFriends(String from, String to, @PathLimit int maxDepth);
    boolean existsPath(String from, String to);
    @PathLimit(3)
    long countPaths(String from, String to);
    Optional<GraphRelationship<Friend, Friend>> findRelationship(Friend from, Friend to);
    List<GraphRelationship<Friend, Friend>> findRelationships(String from, String to);
    Page<GraphRelationship<Friend, Friend>> findRelationshipsByFriends(Pageable pageable);
    long countRelationshipsByColleagues();
    boolean hasRelationship(String from, String to);
}
""")
    }

    @Unroll
    void "test path query for #method"() {
        expect:
        getQuery(findMethod(method)) == query

        where:
        method                              | query
        'findShortestPath'                  | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = shortestPath((s)-[*]-(e)) RETURN p'
        'findShortestPathByFriends'         | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = shortestPath((s)-[:FRIENDS*1..$__pathLimit]->(e)) RETURN p'
        'findPath'                          | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = shortestPath((s)-[*1..4]-(e)) RETURN p'
        'findAllShortestPaths'              | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = allShortestPaths((s)-[*]-(e)) RETURN p'
        'findAllPathsByFriendsOrColleagues' | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 MATCH p = (s)-[:FRIENDS|WORKS_WITH*1..$__pathLimit]-(e) RETURN p ORDER BY length(p)'
        'findAllPathsByAcquaintancesOrFriends' | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 MATCH p = (s)-[:FRIENDS_OF_FRIENDS|FRIENDS*1..$__pathLimit]->(e) RETURN p ORDER BY length(p)'
        'existsPath'                        | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = shortestPath((s)-[*]-(e)) RETURN true AS result LIMIT 1'
        'countPaths'                        | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 MATCH p = (s)-[*1..3]-(e) RETURN count(p) AS result'
        'findRelationship'                  | 'MATCH (s:Friend)-[r]-(e:Friend) WHERE s.name = $p0 AND e.name = $p1 RETURN r, startNode(r) AS f, endNode(r) AS t'
        'findRelationships'                 | 'MATCH (s:Friend)-[r]-(e:Friend) WHERE s.name = $p0 AND e.name = $p1 RETURN r, startNode(r) AS f, endNode(r) AS t'
        'findRelationshipsByFriends'        | 'MATCH (s:Friend)-[r:FRIENDS]->(e:Friend) RETURN r, startNode(r) AS f, endNode(r) AS t'
        'countRelationshipsByColleagues'    | 'MATCH (s:Friend)-[r:WORKS_WITH]->(e:Friend) RETURN count(r) AS result'
        'hasRelationship'                   | 'MATCH (s:Friend)-[r]-(e:Friend) WHERE s.name = $p0 AND e.name = $p1 RETURN true AS result LIMIT 1'
    }

    void "test the operation type of path finders"() {
        expect:
        operationType('findShortestPath') == DataMethod.OperationType.QUERY
        operationType('existsPath') == DataMethod.OperationType.EXISTS
        operationType('countPaths') == DataMethod.OperationType.COUNT
        operationType('countRelationshipsByColleagues') == DataMethod.OperationType.COUNT
    }

    void "test entity endpoints bind the identifier of the argument"() {
        given:
        def method = findMethod('findShortestPath')
        def parameters = method.annotationMetadata.getAnnotation(DataMethod)
            .getAnnotations(DataMethod.META_MEMBER_PARAMETERS, io.micronaut.data.intercept.annotation.DataMethodQueryParameter)

        expect:
        parameters*.intValue(io.micronaut.data.intercept.annotation.DataMethodQueryParameter.META_MEMBER_PARAMETER_INDEX)*.asInt == [0, 1]
        parameters*.stringValues(io.micronaut.data.intercept.annotation.DataMethodQueryParameter.META_MEMBER_PARAMETER_BINDING_PATH).collect { it.toList() } == [['name'], ['name']]
    }

    void "test the path limit parameter is recorded"() {
        expect:
        findMethod('findShortestPathByFriends').annotationMetadata.intValue(PathLimitParameter).asInt == 2
        !findMethod('findPath').annotationMetadata.hasAnnotation(PathLimitParameter)
    }

    void "test a pageable relationship finder has a count query"() {
        expect:
        findMethod('findRelationshipsByFriends').annotationMetadata.stringValue(Query, 'countQuery').get() == 'MATCH (s:Friend)-[r:FRIENDS]->(e:Friend) RETURN count(r) AS result'
    }

    void "test all paths need a limit"() {
        when:
        buildRepository('test.UnboundedRepository', """
import io.micronaut.data.graph.model.*;

$FRIENDS

@TestGraphRepository
interface UnboundedRepository extends CrudRepository<Friend, String> {
    List<GraphPath<Friend>> findAllPaths(String from, String to);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Finding all paths needs a maximum depth')
    }

    void "test the relationship must exist"() {
        when:
        buildRepository('test.UnknownRepository', """
import io.micronaut.data.graph.model.*;

$FRIENDS

@TestGraphRepository
interface UnknownRepository extends CrudRepository<Friend, String> {
    Optional<GraphPath<Friend>> findShortestPathByEnemies(String from, String to);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('has no @Relationship property named [enemies]')
    }

    void "test a finder on a property named path is left to the ordinary finders"() {
        given:
        def repository = buildRepository('test.WaypointRepository', """
import io.micronaut.data.graph.model.*;

@Node
class Waypoint {
    @Id
    private String name;
    private String path;
    private List<String> relationships;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public List<String> getRelationships() { return relationships; }
    public void setRelationships(List<String> relationships) { this.relationships = relationships; }
}

@TestGraphRepository
interface WaypointRepository extends CrudRepository<Waypoint, String> {
    List<String> findPathByName(String name);
    List<String> findPathByNameOrderByPath(String name);
    List<List<String>> findRelationshipsByName(String name);
    List<Waypoint> findByPath(String path);
}
""")

        expect:
        getQuery(repository.executableMethods.find { it.methodName == 'findPathByName' }) == 'MATCH (n:Waypoint) WHERE n.name = $p0 RETURN n.path AS path'
        getQuery(repository.executableMethods.find { it.methodName == 'findPathByNameOrderByPath' }) == 'MATCH (n:Waypoint) WHERE n.name = $p0 RETURN n.path AS path ORDER BY n.path ASC'
        getQuery(repository.executableMethods.find { it.methodName == 'findRelationshipsByName' }) == 'MATCH (n:Waypoint) WHERE n.name = $p0 RETURN n.relationships AS relationships'
        getQuery(repository.executableMethods.find { it.methodName == 'findByPath' }) == 'MATCH (n:Waypoint) WHERE n.path = $p0 RETURN n'
    }

    void "test Between<Id> matches the endpoints by the generated identifier"() {
        given:
        def repository = buildRepository('test.StationRepository', """
import io.micronaut.data.graph.model.*;

@Node
class Station {
    @Id
    @GeneratedValue
    private String id;
    private String name;
    @Relationship("LINKS")
    private List<Station> links = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Station> getLinks() { return links; }
    public void setLinks(List<Station> links) { this.links = links; }
}

@TestGraphRepository
interface StationRepository extends CrudRepository<Station, String> {
    Optional<GraphPath<Station>> findShortestPathByLinksBetweenId(String from, String to);
}
""")
        def stops = buildRepository('test.StopRepository', """
import io.micronaut.data.graph.model.*;

@Node
class Stop {
    @Id
    @GeneratedValue
    private Long id;
    @Relationship("NEXT")
    private Stop next;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Stop getNext() { return next; }
    public void setNext(Stop next) { this.next = next; }
}

@TestGraphRepository
interface StopRepository extends CrudRepository<Stop, Long> {
    @PathLimit(3)
    boolean existsPathByNextBetweenId(Long from, Long to);
}
""")

        expect:
        getQuery(repository.executableMethods.find { it.methodName == 'findShortestPathByLinksBetweenId' }) ==
            'MATCH (s:Station), (e:Station) WHERE elementId(s) = $p0 AND elementId(e) = $p1 AND s <> e MATCH p = shortestPath((s)-[:LINKS*]->(e)) RETURN p'
        getQuery(stops.executableMethods.find { it.methodName == 'existsPathByNextBetweenId' }) ==
            'MATCH (s:Stop), (e:Stop) WHERE id(s) = $p0 AND id(e) = $p1 AND s <> e MATCH p = shortestPath((s)-[:NEXT*1..3]->(e)) RETURN true AS result LIMIT 1'
    }

    void "test a page of relationships has a count query when wrapped in a future or publisher"() {
        given:
        def repository = buildRepository('test.AsyncPageRepository', """
import io.micronaut.data.graph.model.*;
import java.util.concurrent.CompletionStage;
import reactor.core.publisher.Mono;

$FRIENDS

@TestGraphRepository
interface AsyncPageRepository extends CrudRepository<Friend, String> {
    CompletionStage<Page<GraphRelationship<Friend, Friend>>> findRelationships(Pageable pageable);
    Mono<Page<GraphRelationship<Friend, Friend>>> findRelationshipsByFriends(Pageable pageable);
}
""")

        expect:
        repository.executableMethods.find { it.methodName == method }.annotationMetadata.stringValue(Query, 'countQuery').get() == countQuery

        where:
        method                       | countQuery
        'findRelationships'          | 'MATCH (s:Friend)-[r]->(e:Friend) RETURN count(r) AS result'
        'findRelationshipsByFriends' | 'MATCH (s:Friend)-[r:FRIENDS]->(e:Friend) RETURN count(r) AS result'
    }

    void "test the All prefix and count forms on a property are left to the ordinary finders"() {
        given:
        def repository = buildRepository('test.RouteRepository', """
import io.micronaut.data.graph.model.*;

@Node
class Route {
    @Id
    private String name;
    private String paths;
    private String path;
    private String relationships;
    @Relationship("NEXT")
    private List<Route> next = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPaths() { return paths; }
    public void setPaths(String paths) { this.paths = paths; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getRelationships() { return relationships; }
    public void setRelationships(String relationships) { this.relationships = relationships; }
    public List<Route> getNext() { return next; }
    public void setNext(List<Route> next) { this.next = next; }
}

@TestGraphRepository
interface RouteRepository extends CrudRepository<Route, String> {
    List<String> findAllPathsByName(String name);
    long countRelationshipsByName(String name);
    long countRelationshipsByNext(String from, String to);
    boolean existsPathByNext(String from, String to);
}
""")

        expect:
        getQuery(repository.executableMethods.find { it.methodName == method }).contains(fragment)

        where:
        method                     | fragment
        'findAllPathsByName'       | 'WHERE n.name = $p0 RETURN n.paths AS paths'
        'countRelationshipsByName' | 'WHERE n.name = $p0'
        'countRelationshipsByNext' | '[r:NEXT]'
        'existsPathByNext'         | 'shortestPath'
    }

    @Unroll
    void "test invalid path limit is rejected: #declaration"() {
        when:
        buildRepository('test.LimitRepository', """
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.model.*;

$FRIENDS

@TestGraphRepository
interface LimitRepository extends CrudRepository<Friend, String> {
    $declaration
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        declaration                                                                                  | message
        '@PathLimit(0) Optional<GraphPath<Friend>> findShortestPath(String from, String to);'         | 'needs a value of at least 1, found: 0'
        '@PathLimit(-2) boolean existsPath(String from, String to);'                                  | 'needs a value of at least 1, found: -2'
        '@PathLimit Optional<GraphPath<Friend>> findShortestPath(String from, String to);'            | 'needs a value of at least 1, found: -1'
        '@PathLimit(2) List<GraphRelationship<Friend, Friend>> findRelationships();'                  | '@PathLimit does not apply to relationship finders'
        'List<GraphRelationship<Friend, Friend>> findRelationships(String a, String b, @PathLimit int limit);' | '@PathLimit does not apply to relationship finders'
    }

    @Unroll
    void "test path finders reject a tenant-mapped entity: #method"() {
        when:
        buildRepository('test.TenantRepository', """
import io.micronaut.data.graph.model.*;

$TENANT_MEMBER

@TestGraphRepository
interface TenantRepository extends io.micronaut.data.repository.GenericRepository<Member, String> {
    $declaration
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Path and relationship finders do not support the @TenantId of entity [Member]')

        where:
        method              | declaration
        'findRelationships' | 'List<GraphRelationship<Member, Member>> findRelationships();'
        'countRelationships'| 'long countRelationships();'
        'hasRelationship'   | 'boolean hasRelationship();'
        'findShortestPath'  | 'Optional<GraphPath<Member>> findShortestPath(String from, String to);'
    }

    void "test a tenant-mapped entity can opt out of the tenant with WithoutTenantId"() {
        given:
        def repository = buildRepository('test.TenantRepository', """
import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.graph.model.*;

$TENANT_MEMBER

@TestGraphRepository
interface TenantRepository extends io.micronaut.data.repository.GenericRepository<Member, String> {
    @WithoutTenantId
    long countRelationships();
}
""")

        expect:
        getQuery(repository.executableMethods.find { it.methodName == 'countRelationships' }) == 'MATCH (s:Member)-[r]->(e:Member) RETURN count(r) AS result'
    }

    @Unroll
    void "test a database-assigned identifier cannot be primitive: #type"() {
        when:
        buildRepository('test.CounterRepository', """
import io.micronaut.data.graph.model.*;

@Node
class Counter {
    @Id
    @GeneratedValue
    private $type id;

    public $type getId() { return id; }
    public void setId($type id) { this.id = id; }
}

@TestGraphRepository
interface CounterRepository extends io.micronaut.data.repository.GenericRepository<Counter, Long> {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("A database-assigned identifier cannot be primitive, use $wrapper")

        where:
        type   | wrapper
        'long' | 'Long'
        'int'  | 'Integer'
    }

    void "test ordering a path finder is rejected with a clear message"() {
        when:
        buildRepository('test.OrderedRepository', """
import io.micronaut.data.graph.model.*;

$FRIENDS

@TestGraphRepository
interface OrderedRepository extends CrudRepository<Friend, String> {
    List<GraphRelationship<Friend, Friend>> findRelationshipsByFriendsOrderByName();
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Path and relationship finders cannot be ordered, found [findRelationshipsByFriendsOrderByName]')
        !e.message.contains('has no @Relationship property')
    }

    void "test ordering a path finder with bound endpoints is rejected with a clear message"() {
        when:
        buildRepository('test.OrderedRepository', """
import io.micronaut.data.graph.model.*;

$FRIENDS

@TestGraphRepository
interface OrderedRepository extends CrudRepository<Friend, String> {
    GraphPath<Friend> $method(String from, String to);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Path and relationship finders cannot be ordered, found [$method]")
        !e.message.contains('no property named')

        where:
        method << ['findPathBetweenNameOrderByName', 'findPathByFriendsBetweenNameOrderByName']
    }

    void "test path finders must return paths"() {
        when:
        buildRepository('test.WrongReturnRepository', """
import io.micronaut.data.graph.model.*;

$FRIENDS

@TestGraphRepository
interface WrongReturnRepository extends CrudRepository<Friend, String> {
    List<Friend> findShortestPath(String from, String to);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Method must return GraphPath')
    }

    private ExecutableMethod<?, ?> findMethod(String name) {
        def method = repository.executableMethods.find { it.methodName == name }
        assert method != null: "No method $name"
        return method
    }

    private DataMethod.OperationType operationType(String name) {
        return findMethod(name).annotationMetadata.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType).get()
    }
}
