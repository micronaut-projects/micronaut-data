package io.micronaut.data.graph.model.query.builder

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Join
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.graph.annotation.Direction
import io.micronaut.data.graph.annotation.PropertyGraph
import io.micronaut.data.graph.model.query.PathQueryDefinition
import io.micronaut.data.graph.model.query.builder.entities.Person
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQueryBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.model.jpa.criteria.impl.DefaultOrder
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Shared
import spock.lang.Specification

/**
 * Checks the SQL/PGQ rendered for queries built with the criteria API and for path queries.
 */
class OracleGraphCriteriaQuerySpec extends Specification {

    @Shared
    TestEntityRegistry registry = new TestEntityRegistry()

    @Shared
    PersistentEntityCriteriaBuilder cb = new RuntimeCriteriaBuilder(registry)

    void "test select #description"() {
        expect:
        select(builder(), definition) == query

        where:
        description | definition | query
        'where' | { q, r -> q.where(cb.and(cb.equal(r.get("name"), "a"), cb.greaterThan(r.get("age"), 3))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name = ? AND n.age > ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'or' | { q, r -> q.where(cb.or(cb.isNull(r.get("name")), cb.not(cb.isTrue(r.get("active"))))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name IS NULL OR NOT (n.active = TRUE)) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'inList' | { q, r -> q.where(r.get("name").in("a", "b")) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name IN (?, ?)) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'between' | { q, r -> q.where(cb.between(r.get("age"), 1, 9)) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE ((n.age >= ? AND n.age <= ?)) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'join' | { q, r -> q.where(cb.equal(r.join("movies").get("title"), "Matrix")) } | '''SELECT DISTINCT * FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) WHERE (a0.title = ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'order' | { q, r -> q.orderBy(new DefaultOrder(r.get("name"), true, true), cb.desc(r.get("age"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) ORDER BY LOWER("n__name") ASC, "n__age" DESC'''
        'distinctProj' | { q, r -> q.select(r.get("name")).distinct(true) } | '''SELECT DISTINCT "c0" AS "name" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0"))'''
        'count' | { q, r -> q.select(cb.count(r)) } | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "c0"))'''
        'countDistinct' | { q, r -> q.select(cb.countDistinct(r.get("name"))) } | '''SELECT COUNT(DISTINCT "c0") AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0"))'''
        'max' | { q, r -> q.select(cb.max(r.get("age"))) } | '''SELECT MAX("c0") AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.age AS "c0"))'''
        'upper' | { q, r -> q.select(cb.upper(r.get("name"))) } | '''SELECT UPPER("c0") AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0"))'''
        'sum' | { q, r -> q.select(cb.sum(r.get("age"), 1)) } | '''SELECT "c0" AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS ((n.age + ?) AS "c0"))'''
        'fn' | { q, r -> q.select(cb.function("NVL", String, r.get("name"), cb.literal("x"))) } | '''SELECT "c0" AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (NVL(n.name, ?) AS "c0"))'''
        'multi' | { q, r -> q.multiselect(r.get("name").alias("n"), r.get("age")) } | '''SELECT "c0" AS "n", "c1" AS "age" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0", n.age AS "c1"))'''
        'limit' | { q, r -> q.offset(2).limit(5) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) OFFSET 2 ROWS FETCH NEXT 5 ROWS ONLY'''
        'ignoreCase' | { q, r -> q.where(cb.equal(cb.lower(r.get("name")), "a")) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (LOWER(n.name) = ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'notNull' | { q, r -> q.where(cb.isNotNull(r.get("tags"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.tags IS NOT NULL) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'like' | { q, r -> q.where(cb.like(r.get("name"), "a%")) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name LIKE ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'innerJoin' | { q, r -> r.join("movies", Join.Type.INNER); q.select(r) } | '''SELECT DISTINCT * FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        'innerJoinCount' | { q, r -> r.join("movies", Join.Type.INNER); q.select(cb.count(r)) } | '''SELECT COUNT(DISTINCT "c0") AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "c0"))'''
        'innerJoinProjection' | { q, r -> r.join("movies", Join.Type.INNER); q.select(r.get("name")) } | '''SELECT "c0" AS "name" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.name AS "c0"))'''
        'negated isNull relationship' | { q, r -> q.where(cb.not(cb.isNull(r.get("movies")))) } | '''SELECT DISTINCT * FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) WHERE (NOT (a0.title IS NULL)) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
    }

    void "test select #description fails"() {
        when:
        select(builder(), definition)

        then:
        def e = thrown(Exception)
        e.class.simpleName == exception
        e.message == message

        where:
        description | definition | exception | message
        'literal' | { q, r -> q.select(cb.literal(true)).where(cb.equal(r.get("name"), "a")) } | 'IllegalStateException' | '''Parameter expression not supported in selection'''
        'fetch' | { q, r -> r.join("movies", Join.Type.FETCH); q.select(r) } | 'UnsupportedOperationException' | '''Fetching relationships with @Join is not supported for Oracle property graphs: movies'''
        'isNull relationship' | { q, r -> q.where(cb.isNull(r.get("movies"))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is not supported for Oracle property graphs, as a subquery is not allowed in GRAPH_TABLE: write a query with NOT EXISTS outside GRAPH_TABLE using @Query instead: movies'''
        'negated isNotNull relationship' | { q, r -> q.where(cb.not(cb.isNotNull(r.get("movies")))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is not supported for Oracle property graphs, as a subquery is not allowed in GRAPH_TABLE: write a query with NOT EXISTS outside GRAPH_TABLE using @Query instead: movies'''
        'isNull identifier' | { q, r -> q.where(cb.isNull(r.join("movies").get("title"))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is not supported for Oracle property graphs, as a subquery is not allowed in GRAPH_TABLE: write a query with NOT EXISTS outside GRAPH_TABLE using @Query instead: movies.title'''
        'isEmpty identifier' | { q, r -> q.where(cb.isEmptyString(r.join("movies").get("title"))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is not supported for Oracle property graphs, as a subquery is not allowed in GRAPH_TABLE: write a query with NOT EXISTS outside GRAPH_TABLE using @Query instead: movies.title'''
        'order by relationship property' | { q, r -> q.orderBy(cb.asc(r.join("movies").get("released"))) } | 'UnsupportedOperationException' | '''Sorting on a relationship is not supported for Oracle property graphs, which have no OPTIONAL MATCH, so nodes without the relationship would be dropped: movies.released'''
        'order by bound relationship' | { q, r -> q.where(cb.equal(r.join("movies").get("title"), "a")).orderBy(cb.desc(r.join("movies").get("released"))) } | 'UnsupportedOperationException' | '''Sorting on a relationship is not supported for Oracle property graphs, which have no OPTIONAL MATCH, so nodes without the relationship would be dropped: movies.released'''
        'order by lower relationship' | { q, r -> q.orderBy(cb.asc(cb.lower(r.join("movies").get("title")))) } | 'UnsupportedOperationException' | '''Sorting on a relationship is not supported for Oracle property graphs, which have no OPTIONAL MATCH, so nodes without the relationship would be dropped: movies.title'''
    }

    void "test path query #description"() {
        given:
        def person = registry.getEntity(Person)
        def movies = person.getPropertyByName("movies")
        def p1 = cb.parameter(Long)
        def p2 = cb.parameter(Long)
        def definitions = [
            rel: new PathQueryDefinition(person, PathQueryDefinition.Kind.RELATIONSHIPS, PathQueryDefinition.Result.ELEMENTS, [movies], Direction.UNDIRECTED, -1, false, p1, p2),
            relCount: new PathQueryDefinition(person, PathQueryDefinition.Kind.RELATIONSHIPS, PathQueryDefinition.Result.COUNT, [movies], Direction.OUTGOING, -1, false, null, null),
            relExists: new PathQueryDefinition(person, PathQueryDefinition.Kind.RELATIONSHIPS, PathQueryDefinition.Result.EXISTS, [movies], Direction.INCOMING, -1, false, null, null),
            relTwo: new PathQueryDefinition(person, PathQueryDefinition.Kind.RELATIONSHIPS, PathQueryDefinition.Result.ELEMENTS, [movies, movies], Direction.OUTGOING, -1, false, null, null),
            shortest: new PathQueryDefinition(person, PathQueryDefinition.Kind.SHORTEST, PathQueryDefinition.Result.ELEMENTS, [movies], Direction.OUTGOING, 5, false, null, null),
            anyShortest: new PathQueryDefinition(person, PathQueryDefinition.Kind.SHORTEST, PathQueryDefinition.Result.ELEMENTS, [], Direction.OUTGOING, 5, true, null, null),
            allShortestCount: new PathQueryDefinition(person, PathQueryDefinition.Kind.ALL_SHORTEST, PathQueryDefinition.Result.COUNT, [], Direction.OUTGOING, 5, false, p1, p2),
            allExists: new PathQueryDefinition(person, PathQueryDefinition.Kind.ALL, PathQueryDefinition.Result.EXISTS, [movies], Direction.UNDIRECTED, 3, false, p1, null),
            noDepth: new PathQueryDefinition(person, PathQueryDefinition.Kind.ALL, PathQueryDefinition.Result.COUNT, [movies], Direction.OUTGOING, -1, false, null, null),
            boundDepth: new PathQueryDefinition(person, PathQueryDefinition.Kind.ALL, PathQueryDefinition.Result.COUNT, [movies], Direction.OUTGOING, 5, true, null, null),
            allPaths: new PathQueryDefinition(person, PathQueryDefinition.Kind.ALL, PathQueryDefinition.Result.ELEMENTS, [movies], Direction.OUTGOING, 3, false, p1, p2),
            allPathsTwo: new PathQueryDefinition(person, PathQueryDefinition.Kind.ALL, PathQueryDefinition.Result.ELEMENTS, [movies, movies], Direction.UNDIRECTED, 3, false, p1, null),
            allPathsAnyType: new PathQueryDefinition(person, PathQueryDefinition.Kind.ALL, PathQueryDefinition.Result.ELEMENTS, [], Direction.OUTGOING, 3, false, p1, p2),
        ]

        expect:
        expected.startsWith('!')
            ? failure(builder(), definitions[description]) == expected.substring(1)
            : builder().buildPathQuery(definitions[description]).query == expected

        where:
        description | expected
        'rel' | '''SELECT * FROM GRAPH_TABLE (g MATCH (s IS Person) -[r IS ACTED_IN]- (t IS Person) WHERE s.id = ? AND t.id = ? COLUMNS (s.id AS "s__ID_", s.id AS "s__id", 'Person' AS "s__LABEL_", s.name AS "s__name", s.age AS "s__age", s.active AS "s__active", s.status AS "s__status", s.tags AS "s__tags", t.id AS "t__ID_", t.id AS "t__id", 'Person' AS "t__LABEL_", t.name AS "t__name", t.age AS "t__age", t.active AS "t__active", t.status AS "t__status", t.tags AS "t__tags", 'ACTED_IN' AS "r__TYPE_", CASE WHEN s IS SOURCE OF r THEN 1 ELSE 0 END AS "r__FORWARD_"))'''
        'relCount' | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) -[r IS ACTED_IN]-> (t IS Person) COLUMNS (s.id AS "c0"))'''
        'relExists' | '''SELECT 1 AS "result" FROM GRAPH_TABLE (g MATCH (t IS Person) <-[r IS ACTED_IN]- (s IS Person) COLUMNS (s.id AS "c0")) FETCH NEXT 1 ROWS ONLY'''
        'relTwo' | '''!IllegalArgumentException: Relationship finders on Oracle property graphs need exactly one relationship type, for example findRelationshipsByFriends'''
        'shortest' | '''!IllegalArgumentException: Shortest paths are not supported by the SQL/PGQ dialect, which has no shortest path search: return every path up to the depth with findAllPaths'''
        'allShortestCount' | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) -[e]->{1,5} (t IS Person) WHERE s.id = ? AND t.id = ? AND s.id <> t.id AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0"))'''
        'allExists' | '''SELECT 1 AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) -[e IS ACTED_IN]-{1,3} (t IS Person) WHERE s.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0")) FETCH NEXT 1 ROWS ONLY'''
        'noDepth' | '''!IllegalArgumentException: Oracle property graph path queries need a maximum depth: annotate the method with @PathLimit(n), or add an int parameter annotated with @PathLimit'''
        'boundDepth' | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) -[e IS ACTED_IN]->{1,$__pathLimit} (t IS Person) WHERE COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0"))'''
        'allPaths' | '''SELECT * FROM GRAPH_TABLE (g MATCH (s IS Person) -[e IS ACTED_IN]->{1,3} (t IS Person) WHERE s.id = ? AND t.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) ONE ROW PER STEP (v1, x, v2) COLUMNS (MATCHNUM() AS "p__M_", ELEMENT_NUMBER(x) AS "p__I_", v1.id AS "v1__ID_", v1.id AS "v1__id", 'Person' AS "v1__LABEL_", v1.name AS "v1__name", v1.age AS "v1__age", v1.active AS "v1__active", v1.status AS "v1__status", v1.tags AS "v1__tags", v2.id AS "v2__ID_", v2.id AS "v2__id", 'Person' AS "v2__LABEL_", v2.name AS "v2__name", v2.age AS "v2__age", v2.active AS "v2__active", v2.status AS "v2__status", v2.tags AS "v2__tags", 'ACTED_IN' AS "x__TYPE_")) ORDER BY "p__M_", "p__I_"'''
        'allPathsTwo' | '''SELECT * FROM GRAPH_TABLE (g MATCH (s IS Person) -[e IS ACTED_IN]-{1,3} (t IS Person) WHERE s.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) ONE ROW PER STEP (v1, x, v2) COLUMNS (MATCHNUM() AS "p__M_", ELEMENT_NUMBER(x) AS "p__I_", v1.id AS "v1__ID_", v1.id AS "v1__id", 'Person' AS "v1__LABEL_", v1.name AS "v1__name", v1.age AS "v1__age", v1.active AS "v1__active", v1.status AS "v1__status", v1.tags AS "v1__tags", v2.id AS "v2__ID_", v2.id AS "v2__id", 'Person' AS "v2__LABEL_", v2.name AS "v2__name", v2.age AS "v2__age", v2.active AS "v2__active", v2.status AS "v2__status", v2.tags AS "v2__tags", 'ACTED_IN' AS "x__TYPE_")) ORDER BY "p__M_", "p__I_"'''
        'allPathsAnyType' | '''!IllegalArgumentException: Paths over any relationship type are not supported by the SQL/PGQ dialect, which cannot read the label of an edge: name the relationships of the path'''
    }

    void "test entity queries"() {
        given:
        def person = registry.getEntity(Person)
        def b = builder()

        expect:
        b.buildFindByIdQuery(person) == findById
        b.buildFindAllQuery(person) == findAll
        b.buildCountAllQuery(person) == countAll

        where:
        findById | findAll | countAll
        '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE n.id = ? COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))''' | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))''' | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "c0"))'''
    }

    private static String failure(OracleGraphQueryBuilder b, PathQueryDefinition definition) {
        try {
            b.buildPathQuery(definition)
            return "no exception"
        } catch (Exception e) {
            return e.class.simpleName + ": " + e.message
        }
    }

    private static OracleGraphQueryBuilder builder() {
        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(PropertyGraph.name, [value: "g"])
        return new OracleGraphQueryBuilder(metadata)
    }

    private String select(OracleGraphQueryBuilder b, Closure<?> definition) {
        def query = cb.createQuery(Person)
        PersistentEntityRoot root = query.from(Person)
        definition.call(query, root)
        return ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, b).query
    }

    private static final class TestEntityRegistry implements RuntimeEntityRegistry {
        private final Map<Class<?>, RuntimePersistentEntity<?>> entities = [:]

        @Override
        EntityEventListener<Object> getEntityEventListener() {
            throw new IllegalStateException()
        }

        @Override
        Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
            throw new IllegalStateException()
        }

        @Override
        <T> RuntimePersistentEntity<T> getEntity(Class<T> type) {
            RuntimeEntityRegistry registry = this
            return (RuntimePersistentEntity<T>) entities.computeIfAbsent(type, { Class entityType ->
                new RuntimePersistentEntity<Object>(entityType) {
                    @Override
                    protected RuntimePersistentEntity<Object> getEntity(Class<Object> nestedType) {
                        return registry.getEntity(nestedType)
                    }
                }
            })
        }

        @Override
        <T> RuntimePersistentEntity<T> newEntity(Class<T> type) {
            throw new IllegalStateException()
        }

        @Override
        ApplicationContext getApplicationContext() {
            throw new IllegalStateException()
        }
    }
}
