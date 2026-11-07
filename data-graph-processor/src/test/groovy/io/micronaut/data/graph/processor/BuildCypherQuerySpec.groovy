package io.micronaut.data.graph.processor

import io.micronaut.data.graph.model.query.builder.LikePatternConverter
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Relation
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

class BuildCypherQuerySpec extends AbstractGraphDataSpec {

    @Shared
    BeanDefinition<?> movieRepository

    @Shared
    BeanDefinition<?> personRepository

    void setupSpec() {
        movieRepository = buildRepository('test.MovieRepository', """
$MOVIES

@TestGraphRepository
interface MovieRepository extends CrudRepository<Movie, String> {
    List<Movie> findByReleasedGreaterThan(int year);
    Movie findByTitleAndReleased(String title, Integer released);
    List<Movie> findByTitleStartsWithOrderByReleasedDesc(String prefix);
    List<Movie> findByTitleContainsIgnoreCase(String part);
    List<Movie> findByTitleEndsWith(String suffix);
    List<Movie> findByReleasedBetween(int from, int to);
    List<Movie> findByReleasedIn(List<Integer> years);
    List<Movie> findByReleasedNotIn(List<Integer> years);
    List<Movie> findByDescriptionIsNull();
    List<Movie> findByTitleOrDescription(String title, String description);
    long countByReleasedGreaterThan(int year);
    boolean existsByTitle(String title);
    List<Movie> findByDirectorsName(String name);
    List<Movie> findByDirectorsNameOrderByDirectorsBorn(String name);
    List<Movie> findByDirectorsIsEmpty();
    List<Movie> findByRolesIsNotEmpty();
    @Join("directors")
    List<Movie> findByReleasedOrderByDirectorsNameDesc(Integer released);
    List<Movie> findByRolesPersonName(String name);
    List<Movie> findByRolesRolesArrayContains(String role);
    @Join("directors")
    List<Movie> findByReleased(Integer released);
    @Join("roles")
    @Join("directors")
    Optional<Movie> findByTitle(String title);
    List<String> findTitleByReleased(Integer released);
    Integer findMaxReleased();
    List<MovieSummary> findByReleasedLessThan(Integer released);
    void updateReleased(@Id String title, Integer released);
    void deleteByReleasedLessThan(Integer released);
    List<Movie> findTop3OrderByReleasedDesc();
    Page<Movie> findByReleasedGreaterThan(Integer released, Pageable pageable);
    @Query("MATCH (m:Movie) WHERE m.title CONTAINS \$title RETURN m")
    List<Movie> search(String title);
    @Query("MATCH (m:Movie {title: \$title}) SET m.votes = coalesce(m.votes, 0) + 1")
    int vote(String title);
    @Query("MATCH (m:Movie {title: \$title}) DETACH DELETE m")
    void purge(String title);
    @Query("MATCH (m:Movie) WHERE m.title = '\$notAParameter' AND m.released = \$released RETURN m")
    List<Movie> literal(Integer released);
    @Query("MATCH (m:Movie) WHERE m.released IN \$set RETURN count(m)")
    long countIn(List<Integer> set);
    @Query("MATCH (m:Movie:Set {merge: \$merge}) WHERE m.create IS NULL RETURN count(m) AS delete")
    int names(String merge);
    @Query("MATCH (m:Movie {title: \$title}) SET m.set = \$title RETURN count(m)")
    int setSet(String title);
    @Query("MATCH (m:Movie {title: \$title}) SET m.votes = 1; // counted")
    int voteTerminated(String title);
    @Query("MATCH (m:Movie {title: \$title}) CALL { WITH m RETURN 1 AS one } SET m.votes = 1")
    int voteSubquery(String title);
    List<Movie> findByRoles(Role role);
    List<Movie> findByRolesPerson(Person person);
    @Join(value = "directors", type = Join.Type.INNER)
    Page<Movie> findByDescriptionContains(String description, Pageable pageable);
}
""")
        personRepository = buildRepository('test.PersonRepository', """
$MOVIES

@TestGraphRepository
interface PersonRepository extends CrudRepository<Person, String> {
    List<Person> findByMentorName(String name);
    List<Person> findByMentor(Person mentor);
    Optional<Person> findByName(String name);
    @Join("mentor")
    List<Person> findByBornGreaterThan(Integer born);
    List<Person> findByMentorIsNull();
    List<Person> findByMentorIsNotNull();
    @Join(value = "mentor", type = Join.Type.INNER)
    List<Person> findByBornLessThan(Integer born);
    @Join(value = "mentor", type = Join.Type.INNER_FETCH)
    List<Person> findByBorn(Integer born);
    @Join(value = "mentor", type = Join.Type.INNER)
    Page<Person> findByBornGreaterThanEquals(Integer born, Pageable pageable);
    @Join(value = "mentor", type = Join.Type.INNER_FETCH)
    Page<Person> findByBornLessThanEquals(Integer born, Pageable pageable);
    @Join(value = "mentor", type = Join.Type.LEFT)
    Page<Person> findByNameStartsWith(String name, Pageable pageable);
    @Join(value = "mentor", type = Join.Type.INNER)
    List<String> findNameByBornIn(List<Integer> born);
    @Join(value = "mentor", type = Join.Type.INNER)
    long countByBornGreaterThan(Integer born);
}
""")
    }

    @Unroll
    void "test query for #method"() {
        expect:
        getQuery(findMethod(movieRepository, method, arity)) == query

        where:
        method                                     | arity | query
        'findByReleasedGreaterThan'                | 1     | 'MATCH (n:Movie) WHERE n.released > $p0 RETURN n'
        'findByTitleAndReleased'                   | 2     | 'MATCH (n:Movie) WHERE (n.title = $p0 AND n.released = $p1) RETURN n'
        'findByTitleStartsWithOrderByReleasedDesc' | 1     | 'MATCH (n:Movie) WHERE n.title STARTS WITH $p0 RETURN n ORDER BY n.released DESC'
        'findByTitleContainsIgnoreCase'            | 1     | 'MATCH (n:Movie) WHERE toLower(n.title) CONTAINS toLower($p0) RETURN n'
        'findByTitleEndsWith'                      | 1     | 'MATCH (n:Movie) WHERE n.title ENDS WITH $p0 RETURN n'
        'findByReleasedBetween'                    | 2     | 'MATCH (n:Movie) WHERE (n.released >= $p0 AND n.released <= $p1) RETURN n'
        'findByReleasedIn'                         | 1     | 'MATCH (n:Movie) WHERE n.released IN $p0 RETURN n'
        'findByReleasedNotIn'                      | 1     | 'MATCH (n:Movie) WHERE NOT (n.released IN $p0) RETURN n'
        'findByDescriptionIsNull'                  | 0     | 'MATCH (n:Movie) WHERE n.tagline IS NULL RETURN n'
        'findByTitleOrDescription'                 | 2     | 'MATCH (n:Movie) WHERE (n.title = $p0 OR n.tagline = $p1) RETURN n'
        'countByReleasedGreaterThan'               | 1     | 'MATCH (n:Movie) WHERE n.released > $p0 RETURN count(n) AS result'
        'existsByTitle'                            | 1     | 'MATCH (n:Movie) WHERE n.title = $p0 RETURN true AS result LIMIT 1'
        'findByDirectorsName'                      | 1     | 'MATCH (n:Movie), (n)<-[:DIRECTED]-(a0:Person:Actor) WHERE a0.name = $p0 WITH DISTINCT n RETURN n'
        'findByDirectorsNameOrderByDirectorsBorn'  | 1     | 'MATCH (n:Movie), (n)<-[:DIRECTED]-(a0:Person:Actor) WHERE a0.name = $p0 WITH n, min(a0.born) AS o0 RETURN n ORDER BY o0 ASC'
        'findByDirectorsIsEmpty'                   | 0     | 'MATCH (n:Movie) WHERE NOT (n)<-[:DIRECTED]-() RETURN n'
        'findByRolesIsNotEmpty'                    | 0     | 'MATCH (n:Movie) WHERE (n)<-[:ACTED_IN]-() RETURN n'
        'findByReleasedOrderByDirectorsNameDesc'   | 1     | 'MATCH (n:Movie) WHERE n.released = $p0 OPTIONAL MATCH (n)<-[:DIRECTED]-(a0:Person:Actor) WITH n, max(a0.name) AS o0 OPTIONAL MATCH (n)<-[:DIRECTED]-(j0:Person:Actor) WITH n, o0, collect(j0) AS j_directors RETURN n, j_directors ORDER BY o0 DESC'
        'findByRolesPersonName'                    | 1     | 'MATCH (n:Movie), (n)<-[ar0:ACTED_IN]-(a0:Person:Actor) WHERE a0.name = $p0 WITH DISTINCT n RETURN n'
        'findByRoles'                              | 1     | 'MATCH (n:Movie), (n)<-[ar0:ACTED_IN]-(a0:Person:Actor) WHERE elementId(ar0) = $p0 WITH DISTINCT n RETURN n'
        'findByRolesPerson'                        | 1     | 'MATCH (n:Movie), (n)<-[ar0:ACTED_IN]-(a0:Person:Actor) WHERE elementId(a0) = $p0 WITH DISTINCT n RETURN n'
        'findByRolesRolesArrayContains'            | 1     | 'MATCH (n:Movie), (n)<-[ar0:ACTED_IN]-(a0:Person:Actor) WHERE $p0 IN ar0.roles WITH DISTINCT n RETURN n'
        'findByReleased'                           | 1     | 'MATCH (n:Movie) WHERE n.released = $p0 OPTIONAL MATCH (n)<-[:DIRECTED]-(j0:Person:Actor) WITH n, collect(j0) AS j_directors RETURN n, j_directors'
        'findByTitle'                              | 1     | 'MATCH (n:Movie) WHERE n.title = $p0 OPTIONAL MATCH (n)<-[r0:ACTED_IN]-(j0:Person:Actor) WITH n, collect(CASE WHEN r0 IS NULL THEN NULL ELSE {rel: r0, node: j0} END) AS j_roles OPTIONAL MATCH (n)<-[:DIRECTED]-(j1:Person:Actor) WITH n, j_roles, collect(j1) AS j_directors RETURN n, j_roles, j_directors'
        'findTitleByReleased'                      | 1     | 'MATCH (n:Movie) WHERE n.released = $p0 RETURN n.title AS title'
        'findMaxReleased'                          | 0     | 'MATCH (n:Movie) RETURN max(n.released) AS result'
        'findByReleasedLessThan'                   | 1     | 'MATCH (n:Movie) WHERE n.released < $p0 RETURN n.title AS title, n.released AS released'
        'updateReleased'                           | 2     | 'MATCH (n:Movie) WHERE n.title = $p0 SET n.released = $p1 RETURN count(*)'
        'deleteByReleasedLessThan'                 | 1     | 'MATCH (n:Movie) WHERE n.released < $p0 DETACH DELETE n RETURN count(*)'
        'findTop3OrderByReleasedDesc'              | 0     | 'MATCH (n:Movie) RETURN n ORDER BY n.released DESC LIMIT 3'
        'count'                                    | 0     | 'MATCH (n:Movie) RETURN count(n) AS result'
        'findById'                                 | 1     | 'MATCH (n:Movie) WHERE n.title = $p0 RETURN n'
        'deleteById'                               | 1     | 'MATCH (n:Movie) WHERE n.title = $p0 DETACH DELETE n RETURN count(*)'
        'findAll'                                  | 0     | 'MATCH (n:Movie) RETURN n'
    }

    void "test a relationship entity has no dynamic attributes"() {
        when:
        buildRepository('test.StageRepository', """
@RelationshipProperties
class Billing {
    @RelationshipId
    @GeneratedValue
    private Long id;
    @TargetNode
    private Stage stage;
    @DynamicAttributes
    private Map<String, Object> attributes = new HashMap<>();
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Stage getStage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}

@Node
class Stage {
    @Id
    private String name;
    @Relationship("BILLED")
    private List<Billing> billings = new ArrayList<>();
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Billing> getBillings() { return billings; }
    public void setBillings(List<Billing> billings) { this.billings = billings; }
}

@TestGraphRepository
interface StageRepository extends GenericRepository<Stage, String> {
    Optional<Stage> findByName(String name);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('@DynamicAttributes is not supported on a relationship entity')
    }

    void "test the count query of a pageable finder"() {
        given:
        ExecutableMethod<?, ?> method = movieRepository.executableMethods.find {
            it.methodName == 'findByReleasedGreaterThan' && it.argumentTypes.length == 2
        }

        expect:
        getQuery(method) == 'MATCH (n:Movie) WHERE n.released > $p0 RETURN n'
        method.annotationMetadata.stringValue(Query, 'countQuery').get() == 'MATCH (n:Movie) WHERE n.released > $p0 RETURN count(n) AS result'
    }

    @Unroll
    void "test the page and its total of #method agree on the join"() {
        given:
        ExecutableMethod<?, ?> m = findMethod(personRepository, method, 2)

        expect:
        getQuery(m) == query
        m.annotationMetadata.stringValue(Query, 'countQuery').get() == countQuery

        where:
        method                        | query                                                                                                                                  | countQuery
        'findByBornGreaterThanEquals' | 'MATCH (n:Person:Actor), (n)-[:FOLLOWS]->(a0:Person:Actor) WHERE n.born >= $p0 WITH DISTINCT n RETURN n'                               | 'MATCH (n:Person:Actor), (n)-[:FOLLOWS]->(a0:Person:Actor) WHERE n.born >= $p0 WITH DISTINCT n RETURN count(DISTINCT n) AS result'
        'findByBornLessThanEquals'    | 'MATCH (n:Person:Actor) WHERE n.born <= $p0 MATCH (n)-[:FOLLOWS]->(j0:Person:Actor) WITH n, collect(j0) AS j_mentor RETURN n, j_mentor' | 'MATCH (n:Person:Actor), (n)-[:FOLLOWS]->(a0:Person:Actor) WHERE n.born <= $p0 WITH DISTINCT n RETURN count(DISTINCT n) AS result'
        'findByNameStartsWith'        | 'MATCH (n:Person:Actor) WHERE n.name STARTS WITH $p0 RETURN n'                                                                         | 'MATCH (n:Person:Actor) WHERE n.name STARTS WITH $p0 RETURN count(DISTINCT n) AS result'
    }

    void "test the page of a to-many inner join and its total agree on the join"() {
        given:
        ExecutableMethod<?, ?> m = findMethod(movieRepository, 'findByDescriptionContains', 2)

        expect:
        getQuery(m) == 'MATCH (n:Movie), (n)<-[:DIRECTED]-(a0:Person:Actor) WHERE n.tagline CONTAINS $p0 WITH DISTINCT n RETURN n'
        m.annotationMetadata.stringValue(Query, 'countQuery').get() == 'MATCH (n:Movie), (n)<-[:DIRECTED]-(a0:Person:Actor) WHERE n.tagline CONTAINS $p0 WITH DISTINCT n RETURN count(DISTINCT n) AS result'
    }

    void "test a scalar result keeps an inner join"() {
        expect:
        getQuery(findMethod(personRepository, 'findNameByBornIn', 1)) == 'MATCH (n:Person:Actor), (n)-[:FOLLOWS]->(a0:Person:Actor) WHERE n.born IN $p0 WITH DISTINCT n RETURN n.name AS name'
        getQuery(findMethod(personRepository, 'countByBornGreaterThan', 1)) == 'MATCH (n:Person:Actor), (n)-[:FOLLOWS]->(a0:Person:Actor) WHERE n.born > $p0 WITH DISTINCT n RETURN count(DISTINCT n) AS result'
    }

    void "test a relationship entity criterion binds the identifier of what it compares"() {
        expect:
        getParameterProperties(findMethod(movieRepository, 'findByRoles', 1)) == ['roles']
        getParameterProperties(findMethod(movieRepository, 'findByRolesPerson', 1)) == ['roles.person']
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
class Show {
    @Id
    private String title;
    @Relationship(type = "APPEARED_IN", direction = Direction.INCOMING)
    private List<Cameo> cameos = new ArrayList<>();
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<Cameo> getCameos() { return cameos; }
    public void setCameos(List<Cameo> cameos) { this.cameos = cameos; }
}

@TestGraphRepository
interface CastRepository extends CrudRepository<Show, String> {
    List<Show> findByCameos(Cameo cameo);
}
""")

        expect:
        getQuery(findMethod(repository, 'findByCameos', 1)) == 'MATCH (n:Show), (n)<-[ar0:APPEARED_IN]-(a0:Person:Actor) WHERE elementId(a0) = $p0 WITH DISTINCT n RETURN n'
    }

    void "test parameters are bound by name"() {
        expect:
        getParameterNames(findMethod(movieRepository, 'findByTitleAndReleased', 2)) == ['p0', 'p1']
        getParameterNames(findMethod(movieRepository, 'search', 1)) == ['title']
    }

    @Unroll
    void "test explicit query #method is a #operation"() {
        given:
        def m = findMethod(movieRepository, method, 1)

        expect:
        getRawQuery(m) == query
        m.annotationMetadata.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType).get() == operation

        where:
        method    | operation                         | query
        'search'  | DataMethod.OperationType.QUERY    | 'MATCH (m:Movie) WHERE m.title CONTAINS $title RETURN m'
        'vote'    | DataMethod.OperationType.UPDATE   | 'MATCH (m:Movie {title: $title}) SET m.votes = coalesce(m.votes, 0) + 1 RETURN count(*)'
        'purge'   | DataMethod.OperationType.DELETE   | 'MATCH (m:Movie {title: $title}) DETACH DELETE m'
        'literal' | DataMethod.OperationType.QUERY    | 'MATCH (m:Movie) WHERE m.title = \'$notAParameter\' AND m.released = $released RETURN m'
        'countIn' | DataMethod.OperationType.QUERY    | 'MATCH (m:Movie) WHERE m.released IN $set RETURN count(m)'
        'names'   | DataMethod.OperationType.QUERY    | 'MATCH (m:Movie:Set {merge: $merge}) WHERE m.create IS NULL RETURN count(m) AS delete'
        'setSet'  | DataMethod.OperationType.UPDATE   | 'MATCH (m:Movie {title: $title}) SET m.set = $title RETURN count(m)'
        'voteTerminated' | DataMethod.OperationType.UPDATE | 'MATCH (m:Movie {title: $title}) SET m.votes = 1 RETURN count(*); // counted'
        'voteSubquery'   | DataMethod.OperationType.UPDATE | 'MATCH (m:Movie {title: $title}) CALL { WITH m RETURN 1 AS one } SET m.votes = 1 RETURN count(*)'
    }

    void "test parameters inside string literals are ignored"() {
        expect:
        getParameterNames(findMethod(movieRepository, 'literal', 1)) == ['released']
    }

    @Unroll
    void "test person query for #method"() {
        expect:
        getQuery(findMethod(personRepository, method, 1)) == query

        where:
        method                 | query
        'findByMentorName'     | 'MATCH (n:Person:Actor), (n)-[:FOLLOWS]->(a0:Person:Actor) WHERE a0.name = $p0 WITH DISTINCT n RETURN n'
        'findByMentor'         | 'MATCH (n:Person:Actor), (n)-[:FOLLOWS]->(a0:Person:Actor) WHERE elementId(a0) = $p0 WITH DISTINCT n RETURN n'
        'findByName'           | 'MATCH (n:Person:Actor) WHERE n.name = $p0 RETURN n'
        'findById'             | 'MATCH (n:Person:Actor) WHERE elementId(n) = $p0 RETURN n'
        'findByBornGreaterThan'| 'MATCH (n:Person:Actor) WHERE n.born > $p0 OPTIONAL MATCH (n)-[:FOLLOWS]->(j0:Person:Actor) WITH n, collect(j0) AS j_mentor RETURN n, j_mentor'
        'findByBornLessThan'   | 'MATCH (n:Person:Actor), (n)-[:FOLLOWS]->(a0:Person:Actor) WHERE n.born < $p0 WITH DISTINCT n RETURN n'
        'findByBorn'           | 'MATCH (n:Person:Actor) WHERE n.born = $p0 MATCH (n)-[:FOLLOWS]->(j0:Person:Actor) WITH n, collect(j0) AS j_mentor RETURN n, j_mentor'
    }

    @Unroll
    void "test relationship null check for #method"() {
        expect:
        getQuery(findMethod(personRepository, method, 0)) == query

        where:
        method                  | query
        'findByMentorIsNull'    | 'MATCH (n:Person:Actor) WHERE NOT (n)-[:FOLLOWS]->() RETURN n'
        'findByMentorIsNotNull' | 'MATCH (n:Person:Actor) WHERE (n)-[:FOLLOWS]->() RETURN n'
    }

    @Unroll
    void "test #description is rejected"() {
        when:
        buildRepository('test.RejectedRepository', """
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
        description        | method                                                                             | message
        'delete returning' | 'Movie deleteByTitleReturning(String title);'                                      | 'Returning updates and deletes are not supported'
        'update returning' | 'Movie updateByTitleReturning(String title, Integer released);'                    | 'Returning updates and deletes are not supported'
        'right join'       | '@Join(value = "directors", type = Join.Type.RIGHT) List<Movie> findByReleased(Integer r);' | 'only support LEFT and INNER joins'
    }

    @Unroll
    void "test a #description stored as the lock property is rejected"() {
        when:
        buildRepository('test.LockedRepository', """
@Node
class Locked {
    @Id
    private String code;
    $property
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getValue() { return value; }
    public void setValue(Long value) { this.value = value; }
}

@TestGraphRepository
interface LockedRepository extends CrudRepository<Locked, String> {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Property name [__lock] is reserved')

        where:
        description        | property
        'property'         | '@MappedProperty("__lock") private Long value;'
        'version property' | '@Version @MappedProperty("__lock") private Long value;'
    }

    void "test a relationship property is free to use the name of the lock property"() {
        when:
        buildRepository('test.LockedRepository', """
@Node
class Locked {
    @Id
    private String code;
    @Relationship(type = "KNOWS")
    private List<Knows> friends;
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public List<Knows> getFriends() { return friends; }
    public void setFriends(List<Knows> friends) { this.friends = friends; }
}

@RelationshipProperties
class Knows {
    @TargetNode
    private Locked friend;
    @MappedProperty("__lock")
    private Long value;
    public Locked getFriend() { return friend; }
    public void setFriend(Locked friend) { this.friend = friend; }
    public Long getValue() { return value; }
    public void setValue(Long value) { this.value = value; }
}

@TestGraphRepository
interface LockedRepository extends CrudRepository<Locked, String> {
}
""")

        then:
        noExceptionThrown()
    }

    void "test array relationships are rejected"() {
        when:
        buildRepository('test.ArrayRepository', """
@Node
class Crew {
    @Id
    private String name;
    @Relationship(type = "KNOWS")
    private Crew[] friends;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Crew[] getFriends() { return friends; }
    public void setFriends(Crew[] friends) { this.friends = friends; }
}

@TestGraphRepository
interface ArrayRepository extends CrudRepository<Crew, String> {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('A relationship cannot be an array')
    }

    void "test relationship kinds are inferred from the property type"() {
        given:
        def introspection = buildBeanIntrospection('test.Movie', """
package test;

import io.micronaut.data.annotation.*;
import io.micronaut.data.graph.annotation.*;
import java.util.*;

$MOVIES
""")

        expect:
        introspection.getRequiredProperty('roles', List).annotationMetadata.enumValue(Relation, Relation.Kind).get() == Relation.Kind.ONE_TO_MANY
        introspection.getRequiredProperty('directors', List).annotationMetadata.enumValue(Relation, Relation.Kind).get() == Relation.Kind.ONE_TO_MANY
    }

    void "test single relationships are many to one"() {
        given:
        def introspection = buildBeanIntrospection('test.Person', """
package test;

import io.micronaut.data.annotation.*;
import io.micronaut.data.graph.annotation.*;
import java.util.*;

$MOVIES
""")

        expect:
        introspection.getRequiredProperty('mentor', Object).annotationMetadata.enumValue(Relation, Relation.Kind).get() == Relation.Kind.MANY_TO_ONE
    }

    void "test dynamic relationships are collected apart from declared ones"() {
        when:
        def repository = buildRepository('test.PetRepository', '''
@Node
class Pet {
    @Id
    private String name;
    @Relationship("FOLLOWS")
    private Pet leader;
    @DynamicRelationships
    private Map<String, List<Object>> related = new HashMap<>();
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Pet getLeader() { return leader; }
    public void setLeader(Pet leader) { this.leader = leader; }
    public Map<String, List<Object>> getRelated() { return related; }
    public void setRelated(Map<String, List<Object>> related) { this.related = related; }
}

@TestGraphRepository
interface PetRepository extends CrudRepository<Pet, String> {
    List<Pet> findByNameStartsWith(String prefix);
}
''')
        def query = getQuery(findMethod(repository, 'findByNameStartsWith', 1))

        then:
        query.startsWith('MATCH (n:Pet) WHERE n.name STARTS WITH $p0 OPTIONAL MATCH (n)-[dr]->(dn) WHERE NOT type(dr) IN ')
        query.contains("'FOLLOWS'")
        query.contains('collect(CASE WHEN dr IS NULL THEN NULL ELSE {type: type(dr), node: dn} END) AS d_relationships')
        query.endsWith('d_relationships')
    }

    void "test DTOs must be serdeable"() {
        when:
        buildRepository('test.DtoRepository', """
$MOVIES

@io.micronaut.core.annotation.Introspected
class TitleOnly {
    private String title;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}

@TestGraphRepository
interface DtoRepository extends CrudRepository<Movie, String> {
    List<TitleOnly> findByReleased(Integer released);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Graph repositories read DTOs with Micronaut Serialization: annotate [test.TitleOnly] with @Serdeable')
    }

    void "test @Projection on both the method and the DTO is rejected"() {
        when:
        buildRepository('test.MixedRepository', """
$MOVIES

@io.micronaut.serde.annotation.Serdeable
record TitleAndYear(String label, @io.micronaut.data.annotation.Projection("released") Integer year) {
}

@TestGraphRepository
interface MixedRepository extends CrudRepository<Movie, String> {
    @Query("MATCH (n:Movie) RETURN n.title AS title, n.released AS released")
    @io.micronaut.data.annotation.Projection("title")
    List<TitleAndYear> mixed();
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Declare @Projection either on the method or on the properties of [test.TitleAndYear], not both')
    }

    void "test @Projection on the method alone or the DTO alone compiles"() {
        when:
        def repository = buildRepository('test.ProjectionRepository', """
$MOVIES

@io.micronaut.serde.annotation.Serdeable
record ByMethod(String label) {
}

@io.micronaut.serde.annotation.Serdeable
record ByProperty(@io.micronaut.data.annotation.Projection("title") String label) {
}

@TestGraphRepository
interface ProjectionRepository extends CrudRepository<Movie, String> {
    @Query("MATCH (n:Movie) RETURN n.title AS title")
    @io.micronaut.data.annotation.Projection("title")
    List<ByMethod> byMethod();

    @Query("MATCH (n:Movie) RETURN n.title AS title")
    List<ByProperty> byProperty();
}
""")

        then:
        findMethod(repository, 'byMethod', 0) != null
        findMethod(repository, 'byProperty', 0) != null
    }

    void "test Object[] rows are read without a deserializer"() {
        when:
        def repository = buildRepository('test.RowsRepository', """
$MOVIES

@TestGraphRepository
interface RowsRepository extends CrudRepository<Movie, String> {
    @Query("MATCH (n:Movie) RETURN n.title AS title, n.released AS released")
    List<Object[]> titlesAndYears();
}
""")

        then:
        getQuery(findMethod(repository, 'titlesAndYears', 0)) == 'MATCH (n:Movie) RETURN n.title AS title, n.released AS released'
    }

    void "test names that cannot be quoted are rejected"() {
        when:
        buildRepository('test.HostileRepository', """
@Node("Movie`) DETACH DELETE (n")
class Hostile {
    @Id
    private String title;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}

@TestGraphRepository
interface HostileRepository extends CrudRepository<Hostile, String> {
    List<Hostile> findByTitle(String title);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('cannot appear in a graph query')
    }

    void "test LIKE is matched as a regular expression built from the pattern"() {
        when:
        def repository = buildRepository('test.LikeRepository', """
$MOVIES

@TestGraphRepository
interface LikeRepository extends CrudRepository<Movie, String> {
    List<Movie> findByTitleLike(String title);
    List<Movie> findByTitleNotLike(String title);
}
""")
        def like = repository.executableMethods.find { it.methodName == 'findByTitleLike' }
        def notLike = repository.executableMethods.find { it.methodName == 'findByTitleNotLike' }

        then: "the pattern is translated when it is bound"
        getQuery(like) == "MATCH (n:Movie) WHERE n.title =~ ('(?s)' + \$p0) RETURN n"
        like.getAnnotation(DataMethod).getAnnotations('parameters', DataMethodQueryParameter).first().classValue('converter').get() == LikePatternConverter
        getQuery(notLike) == "MATCH (n:Movie) WHERE NOT (n.title =~ ('(?s)' + \$p0)) RETURN n"
    }

    void "test joins deeper than one relationship fetch nested nodes"() {
        when:
        def repository = buildRepository('test.DeepRepository', """
$MOVIES

@TestGraphRepository
interface DeepRepository extends CrudRepository<Movie, String> {
    @Join("directors.mentor")
    List<Movie> findByReleased(Integer released);
}
""")

        then:
        getQuery(findMethod(repository, 'findByReleased', 1)) == 'MATCH (n:Movie) WHERE n.released = $p0 OPTIONAL MATCH (n)<-[:DIRECTED]-(j0:Person:Actor) OPTIONAL MATCH (j0)-[:FOLLOWS]->(j0_0n:Person:Actor) WITH n, j0, collect(DISTINCT j0_0n) AS j0_0 WITH n, collect(DISTINCT CASE WHEN j0 IS NULL THEN NULL ELSE {node: j0, nested: {mentor: j0_0}} END) AS j_directors RETURN n, j_directors'
    }

    void "test nested joins that share a relationship fetch every branch"() {
        when:
        def repository = buildRepository('test.BranchRepository', """
@Node
class Film {
    @Id
    private String title;
    @Relationship(type = "DIRECTED", direction = Direction.INCOMING)
    private List<Director> directors = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<Director> getDirectors() { return directors; }
    public void setDirectors(List<Director> directors) { this.directors = directors; }
}

@Node
class Director {
    @Id
    private String name;
    @Relationship("FOLLOWS")
    private Director mentor;
    @Relationship("WON")
    private List<Award> awards = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Director getMentor() { return mentor; }
    public void setMentor(Director mentor) { this.mentor = mentor; }
    public List<Award> getAwards() { return awards; }
    public void setAwards(List<Award> awards) { this.awards = awards; }
}

@Node
class Award {
    @Id
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@TestGraphRepository
interface BranchRepository extends CrudRepository<Film, String> {
    @Join("directors.mentor")
    @Join("directors.awards")
    List<Film> findByTitle(String title);
}
""")

        then:
        getQuery(findMethod(repository, 'findByTitle', 1)) == 'MATCH (n:Film) WHERE n.title = $p0 OPTIONAL MATCH (n)<-[:DIRECTED]-(j0:Director) OPTIONAL MATCH (j0)-[:WON]->(j0_0n:Award) WITH n, j0, collect(DISTINCT j0_0n) AS j0_0 OPTIONAL MATCH (j0)-[:FOLLOWS]->(j0_1n:Director) WITH n, j0, j0_0, collect(DISTINCT j0_1n) AS j0_1 WITH n, collect(DISTINCT CASE WHEN j0 IS NULL THEN NULL ELSE {node: j0, nested: {awards: j0_0, mentor: j0_1}} END) AS j_directors RETURN n, j_directors'
    }

    @Unroll
    void "test nested joins keep the join type for #description"() {
        when:
        def repository = buildRepository('test.' + name + 'Repository', """
$MOVIES

@TestGraphRepository
interface ${name}Repository extends CrudRepository<Movie, String> {
    $joins
    List<Movie> findAll();
}
""")

        then:
        getQuery(findMethod(repository, 'findAll', 0)).startsWith(prefix + ' WITH n, collect(DISTINCT CASE WHEN j0 IS NULL')

        where:
        description          | name          | joins                                                                                                 | prefix
        'inner at each step' | 'InnerInner'  | '@Join(value = "directors", type = Join.Type.INNER_FETCH) @Join(value = "directors.mentor", type = Join.Type.INNER_FETCH)' | 'MATCH (n:Movie) MATCH (n)<-[:DIRECTED]-(j0:Person:Actor) MATCH (j0)-[:FOLLOWS]->(j0_0n:Person:Actor) WITH n, j0, collect(DISTINCT j0_0n) AS j0_0'
        'an inner parent'    | 'InnerLeft'   | '@Join("directors.mentor") @Join(value = "directors", type = Join.Type.INNER_FETCH)'                  | 'MATCH (n:Movie) MATCH (n)<-[:DIRECTED]-(j0:Person:Actor) OPTIONAL MATCH (j0)-[:FOLLOWS]->(j0_0n:Person:Actor) WITH n, j0, collect(DISTINCT j0_0n) AS j0_0'
        'an inner child'     | 'LeftInner'   | '@Join("directors") @Join(value = "directors.mentor", type = Join.Type.INNER_FETCH)'                  | 'MATCH (n:Movie) MATCH (n)<-[:DIRECTED]-(j0:Person:Actor) MATCH (j0)-[:FOLLOWS]->(j0_0n:Person:Actor) WITH n, j0, collect(DISTINCT j0_0n) AS j0_0'
        'only an inner path' | 'InnerPath'   | '@Join(value = "directors.mentor", type = Join.Type.INNER_FETCH)'                                     | 'MATCH (n:Movie) MATCH (n)<-[:DIRECTED]-(j0:Person:Actor) MATCH (j0)-[:FOLLOWS]->(j0_0n:Person:Actor) WITH n, j0, collect(DISTINCT j0_0n) AS j0_0'
        'left joins'         | 'LeftLeft'    | '@Join("directors") @Join("directors.mentor")'                                                        | 'MATCH (n:Movie) OPTIONAL MATCH (n)<-[:DIRECTED]-(j0:Person:Actor) OPTIONAL MATCH (j0)-[:FOLLOWS]->(j0_0n:Person:Actor) WITH n, j0, collect(DISTINCT j0_0n) AS j0_0'
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> definition, String name, int arity) {
        def method = definition.executableMethods.find { it.methodName == name && it.argumentTypes.length == arity }
        assert method != null: "No method $name with $arity arguments"
        return method
    }
}
