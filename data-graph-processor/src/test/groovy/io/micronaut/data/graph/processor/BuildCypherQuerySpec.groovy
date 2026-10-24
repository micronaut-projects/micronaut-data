package io.micronaut.data.graph.processor

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
        'findByRolesPersonName'                    | 1     | 'MATCH (n:Movie), (n)<-[ar0:ACTED_IN]-(a0:Person:Actor) WHERE a0.name = $p0 WITH DISTINCT n RETURN n'
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

    void "test the count query of a pageable finder"() {
        given:
        ExecutableMethod<?, ?> method = movieRepository.executableMethods.find {
            it.methodName == 'findByReleasedGreaterThan' && it.argumentTypes.length == 2
        }

        expect:
        getQuery(method) == 'MATCH (n:Movie) WHERE n.released > $p0 RETURN n'
        method.annotationMetadata.stringValue(Query, 'countQuery').get() == 'MATCH (n:Movie) WHERE n.released > $p0 RETURN count(n) AS result'
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
        getQuery(m) == query
        m.annotationMetadata.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType).get() == operation

        where:
        method    | operation                         | query
        'search'  | DataMethod.OperationType.QUERY    | 'MATCH (m:Movie) WHERE m.title CONTAINS $title RETURN m'
        'vote'    | DataMethod.OperationType.UPDATE   | 'MATCH (m:Movie {title: $title}) SET m.votes = coalesce(m.votes, 0) + 1'
        'purge'   | DataMethod.OperationType.DELETE   | 'MATCH (m:Movie {title: $title}) DETACH DELETE m'
        'literal' | DataMethod.OperationType.QUERY    | 'MATCH (m:Movie) WHERE m.title = \'$notAParameter\' AND m.released = $released RETURN m'
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

    void "test LIKE is rejected"() {
        when:
        buildRepository('test.LikeRepository', """
$MOVIES

@TestGraphRepository
interface LikeRepository extends CrudRepository<Movie, String> {
    List<Movie> findByTitleLike(String title);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('LIKE is not supported by graph repositories')
    }

    void "test joins deeper than one relationship are rejected"() {
        when:
        buildRepository('test.DeepRepository', """
$MOVIES

@TestGraphRepository
interface DeepRepository extends CrudRepository<Movie, String> {
    @Join("directors.mentor")
    List<Movie> findByReleased(Integer released);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('can only join direct relationships')
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> definition, String name, int arity) {
        def method = definition.executableMethods.find { it.methodName == name && it.argumentTypes.length == arity }
        assert method != null: "No method $name with $arity arguments"
        return method
    }
}
