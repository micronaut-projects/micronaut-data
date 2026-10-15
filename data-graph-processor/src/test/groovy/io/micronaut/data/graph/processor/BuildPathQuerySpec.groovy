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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Friend> getFriends() { return friends; }
    public void setFriends(List<Friend> friends) { this.friends = friends; }
    public List<Friend> getColleagues() { return colleagues; }
    public void setColleagues(List<Friend> colleagues) { this.colleagues = colleagues; }
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
