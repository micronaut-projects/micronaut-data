package io.micronaut.data.graph.processor

import io.micronaut.data.annotation.Query
import io.micronaut.data.graph.model.query.PathDirectionParameter
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

    @Shared
    BeanDefinition<?> repository

    @Shared
    BeanDefinition<?> movies

    void setupSpec() {
        repository = buildRepository('test.FriendRepository', """
import io.micronaut.data.graph.model.*;

$FRIENDS

@TestGraphRepository
interface FriendRepository extends CrudRepository<Friend, String> {
    @PathLimit(3)
    boolean existsPathByFriends(String from, String to, @PathDirection Direction direction);
    List<GraphRelationship<Friend, Friend>> findRelationshipsByFriends(String from, String to, @PathDirection Direction direction);
    long countRelationshipsByFriends(@PathDirection Direction direction);
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
        movies = buildRepository('test.MovieRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestGraphRepository
interface MovieRepository extends CrudRepository<Movie, String> {
    List<Role> findRelationshipsByRoles(String title, String person);
    Optional<Role> findRelationship(Movie movie, Person person);
    Page<Role> findRelationshipsByRoles(Pageable pageable);
    List<GraphRelationship<Person, Movie>> findRelationshipsByDirectors(String title, String director);
    long countRelationshipsByRoles();
    boolean existsRelationshipByDirectorsBetweenTitleAndName(String title, String name);
    @PathLimit(3)
    List<GraphPath<Object>> findAllPathsToPerson(String title, String person);
    Optional<GraphPath<Object>> findShortestPath(Movie from, Person to);
    @PathLimit(2)
    boolean existsPathByDirectorsBetweenTitleAndName(String title, String name);
    @PathLimit(2)
    long countPathsToPersonBetweenTitleAndName(String title, String name, @PathDirection Direction direction);
}
""")
    }

    @Unroll
    void "test path query with a direction argument for #method"() {
        given:
        def found = findMethod(repository, method, parameters)

        expect:
        getQuery(found) == query
        found.annotationMetadata.intValue(PathDirectionParameter).asInt == parameters - 1
        found.annotationMetadata.booleanValue(PathDirectionParameter, 'outgoingIfUndirected').orElse(false) == outgoingIfUndirected

        where:
        method                      | parameters | outgoingIfUndirected | query
        'existsPathByFriends'       | 3     | false                | 'MATCH (s:Friend), (e:Friend) WHERE s.name = $p0 AND e.name = $p1 AND s <> e MATCH p = shortestPath((s)$__pathLeft[:FRIENDS*1..3]$__pathRight(e)) RETURN true AS result LIMIT 1'
        'findRelationshipsByFriends' | 3    | false                | 'MATCH (s:Friend)$__pathLeft[r:FRIENDS]$__pathRight(e:Friend) WHERE s.name = $p0 AND e.name = $p1 RETURN r, startNode(r) AS f, endNode(r) AS t'
        'countRelationshipsByFriends' | 1   | true                 | 'MATCH (s:Friend)$__pathLeft[r:FRIENDS]$__pathRight(e:Friend) RETURN count(r) AS result'
    }

    @Unroll
    void "test path query between movies and people for #method"() {
        expect:
        getQuery(findMethod(movies, method, parameters)) == query

        where:
        method                                          | parameters | query
        'findRelationshipsByRoles'                      | 2 | 'MATCH (s:Movie)<-[r:ACTED_IN]-(e:Person:Actor) WHERE s.title = $p0 AND elementId(e) = $p1 RETURN r, s AS f, e AS t'
        'findRelationship'                              | 2 | 'MATCH (s:Movie)<-[r:ACTED_IN]-(e:Person:Actor) WHERE s.title = $p0 AND elementId(e) = $p1 RETURN r, s AS f, e AS t'
        'findRelationshipsByDirectors'                  | 2 | 'MATCH (s:Movie)<-[r:DIRECTED]-(e:Person:Actor) WHERE s.title = $p0 AND elementId(e) = $p1 RETURN r, startNode(r) AS f, endNode(r) AS t'
        'countRelationshipsByRoles'                     | 0 | 'MATCH (s:Movie)<-[r:ACTED_IN]-(e:Person:Actor) RETURN count(r) AS result'
        'existsRelationshipByDirectorsBetweenTitleAndName' | 2 | 'MATCH (s:Movie)<-[r:DIRECTED]-(e:Person:Actor) WHERE s.title = $p0 AND e.name = $p1 RETURN true AS result LIMIT 1'
        'findAllPathsToPerson'                          | 2 | 'MATCH (s:Movie), (e:Person:Actor) WHERE s.title = $p0 AND elementId(e) = $p1 MATCH p = (s)-[*1..3]-(e) RETURN p ORDER BY length(p)'
        'findShortestPath'                              | 2 | 'MATCH (s:Movie), (e:Person:Actor) WHERE s.title = $p0 AND elementId(e) = $p1 MATCH p = shortestPath((s)-[*]-(e)) RETURN p'
        'existsPathByDirectorsBetweenTitleAndName'      | 2 | 'MATCH (s:Movie), (e:Person:Actor) WHERE s.title = $p0 AND e.name = $p1 MATCH p = shortestPath((s)<-[:DIRECTED*1..2]-(e)) RETURN true AS result LIMIT 1'
        'countPathsToPersonBetweenTitleAndName'         | 3 | 'MATCH (s:Movie), (e:Person:Actor) WHERE s.title = $p0 AND e.name = $p1 MATCH p = (s)$__pathLeft[*1..2]$__pathRight(e) RETURN count(p) AS result'
    }

    void "test a page of relationship entities has a count query"() {
        expect:
        findMethod(movies, 'findRelationshipsByRoles', 1).annotationMetadata.stringValue(Query, 'countQuery').get() == 'MATCH (s:Movie)<-[r:ACTED_IN]-(e:Person:Actor) RETURN count(r) AS result'
    }

    void "test entity endpoints of different types bind the identifier of each"() {
        given:
        def method = findMethod(movies, 'findShortestPath')
        def parameters = method.annotationMetadata.getAnnotation(DataMethod)
            .getAnnotations(DataMethod.META_MEMBER_PARAMETERS, io.micronaut.data.intercept.annotation.DataMethodQueryParameter)

        expect:
        parameters*.stringValues(io.micronaut.data.intercept.annotation.DataMethodQueryParameter.META_MEMBER_PARAMETER_BINDING_PATH).collect { it.toList() } == [['title'], ['id']]
    }

    @Unroll
    void "test #description is rejected"() {
        when:
        buildRepository('test.RejectedRepository', """
import io.micronaut.data.graph.model.*;

$MOVIES

@TestGraphRepository
interface RejectedRepository extends CrudRepository<Movie, String> {
    $method
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        description                              | method                                                                                   | message
        'a relationship entity of another property' | 'List<Role> findRelationshipsByDirectors(String title, String director);'              | 'is not a [Role], return GraphRelationship instead'
        'a relationship entity with no property' | 'List<Movie> findRelationships(String title, String director);'                           | 'Method must return GraphRelationship or a @RelationshipProperties entity'
        'an unknown end entity'                  | '@PathLimit(2) boolean existsPathToStudio(String title, String studio);'                 | 'No entity named [Studio] is reachable from [Movie]'
        'an end entity that is not the parameter' | '@PathLimit(2) boolean existsPathToPerson(Movie from, Movie to);'                       | 'The end node is a [Person] but parameter [to] is a Movie'
        'a direction parameter of another type'  | '@PathLimit(2) boolean existsPath(String from, String to, @PathDirection String direction);' | 'must be a io.micronaut.data.graph.annotation.Direction'
        'three endpoint properties'              | '@PathLimit(2) boolean existsPathToPersonBetweenTitleAndNameAndBorn(String title, String name);' | 'Between names the property of the start node and the property of the end node'
        'a property of the wrong endpoint'       | '@PathLimit(2) boolean existsPathToPersonBetweenName(String title, String name);'        | 'Entity [Movie] has no property named [name]'
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
        findMethod(repository, name)
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> definition, String name, int parameters = -1) {
        def method = definition.executableMethods.find { it.methodName == name && (parameters < 0 || it.arguments.length == parameters) }
        assert method != null: "No method $name"
        return method
    }

    private DataMethod.OperationType operationType(String name) {
        return findMethod(name).annotationMetadata.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType).get()
    }
}
