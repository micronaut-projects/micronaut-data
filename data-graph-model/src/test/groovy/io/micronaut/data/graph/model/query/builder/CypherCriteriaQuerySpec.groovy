package io.micronaut.data.graph.model.query.builder

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Join
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.graph.model.criteria.DynamicPropertyExpression
import io.micronaut.data.graph.model.criteria.GraphCriteria
import io.micronaut.data.graph.model.criteria.RelatedPredicate
import io.micronaut.data.graph.model.query.builder.entities.Movie
import io.micronaut.data.graph.model.query.builder.entities.Note
import io.micronaut.data.graph.model.query.builder.entities.Person
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.model.jpa.criteria.impl.DefaultOrder
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQueryBuilder
import io.micronaut.data.model.query.builder.QueryResult
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.Nulls
import jakarta.persistence.criteria.Root
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Checks the Cypher rendered for queries built with the criteria API, which reaches expressions that finders do not.
 */
class CypherCriteriaQuerySpec extends Specification {

    @Shared
    PersistentEntityCriteriaBuilder cb = new RuntimeCriteriaBuilder(new TestEntityRegistry())

    @Unroll
    void "test select query for #description"() {
        expect:
        select(definition) == query

        where:
        description            | definition                                                                                                                                  | query
        'distinct join'        | { q, r -> q.select(r).distinct(true).where(cb.equal(r.join("movies").get("title"), "Matrix")) }                                            | 'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) WHERE a0.title = $p0 WITH DISTINCT n RETURN DISTINCT n'
        'distinct projection'  | { q, r -> q.select(r.get("name")).distinct(true) }                                                                                         | 'MATCH (n:Person) RETURN DISTINCT n.name AS name'
        'projection with join' | { q, r -> q.select(r.get("name")).where(cb.equal(r.join("movies").get("title"), "Matrix")) }                                               | 'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) WHERE a0.title = $p0 WITH DISTINCT n RETURN n.name AS name'
        'joined projection'    | { q, r -> q.select(r.join("movies").get("title")) }                                                                                        | 'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) RETURN a0.title AS title'
        'compound selection'   | { q, r -> q.multiselect(r.get("name").alias("n"), r.get("age"), cb.count(r).alias("total")) }                                              | 'MATCH (n:Person) RETURN n.name AS n, n.age AS age, count(n) AS total'
        'id selection'         | { q, r -> q.select(r.id()) }                                                                                                               | 'MATCH (n:Person) RETURN id(n) AS id'
        'sum'                  | { q, r -> q.select(cb.sum(r.get("age"), 1)) }                                                                                              | 'MATCH (n:Person) RETURN (n.age + $p0) AS result'
        'product and quotient' | { q, r -> q.select(cb.quot(cb.prod(r.get("age"), 2), 3)) }                                                                                 | 'MATCH (n:Person) RETURN ((n.age * $p0) / $p1) AS result'
        'concat'               | { q, r -> q.select(cb.concat(r.get("name"), "!")) }                                                                                        | 'MATCH (n:Person) RETURN (n.name + $p0) AS result'
        'function'             | { q, r -> q.select(cb.function("toUpper", String, r.get("name"))) }                                                                        | 'MATCH (n:Person) RETURN toUpper(n.name) AS result'
        'function arguments'   | { q, r -> q.select(cb.function("coalesce", String, r.get("name"), cb.literal("x"))) }                                                      | 'MATCH (n:Person) RETURN coalesce(n.name, $p0) AS result'
        'upper, lower, length' | { q, r -> q.select(cb.upper(r.get("name"))).where(cb.equal(cb.length(cb.lower(r.get("name"))), 3)) }                                      | 'MATCH (n:Person) WHERE size(toLower(n.name)) = $p0 RETURN toUpper(n.name) AS result'
        'count distinct'       | { q, r -> q.select(cb.countDistinct(r.get("name"))) }                                                                                      | 'MATCH (n:Person) RETURN count(DISTINCT n.name) AS result'
        'count with join'      | { q, r -> q.select(cb.count(r)).where(cb.equal(r.join("movies").get("title"), "Matrix")) }                                                 | 'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) WHERE a0.title = $p0 WITH DISTINCT n RETURN count(DISTINCT n) AS result'
        'current date'         | { q, r -> q.where(cb.lessThan(r.get("name"), cb.currentDate())) }                                                                          | 'MATCH (n:Person) WHERE n.name < date() RETURN n'
        'current time'         | { q, r -> q.where(cb.lessThan(r.get("name"), cb.currentTime())) }                                                                          | 'MATCH (n:Person) WHERE n.name < localtime() RETURN n'
        'current timestamp'    | { q, r -> q.where(cb.lessThan(r.get("name"), cb.currentTimestamp())) }                                                                     | 'MATCH (n:Person) WHERE n.name < datetime() RETURN n'
        'ignore case order'    | { q, r -> q.orderBy(new DefaultOrder(r.get("name"), true, true), cb.desc(r.get("age"))) }                                                  | 'MATCH (n:Person) RETURN n ORDER BY toLower(n.name) ASC, n.age DESC'
        'null precedence'      | { q, r -> q.orderBy(new DefaultOrder(r.get("name"), true, false, Nulls.FIRST), new DefaultOrder(r.get("age"), false, false, Nulls.LAST)) } | 'MATCH (n:Person) RETURN n ORDER BY CASE WHEN n.name IS NULL THEN 0 ELSE 1 END, n.name ASC, CASE WHEN n.age IS NULL THEN 1 ELSE 0 END, n.age DESC'
        'offset and limit'     | { q, r -> q.offset(5).limit(10) }                                                                                                          | 'MATCH (n:Person) RETURN n SKIP 5 LIMIT 10'
        'empty conjunction'    | { q, r -> q.where(cb.and()) }                                                                                                              | 'MATCH (n:Person) RETURN n'
        'related'              | { q, r -> q.where(GraphCriteria.related(r, "movies")) }                                                                                    | 'MATCH (n:Person) WHERE (n)-[:ACTED_IN]->() RETURN n'
        'not related'          | { q, r -> q.where(cb.not(GraphCriteria.related(r, "movies"))) }                                                                            | 'MATCH (n:Person) WHERE NOT ((n)-[:ACTED_IN]->()) RETURN n'
        'related to'           | { q, r -> q.where(GraphCriteria.relatedTo(r, "movies", cb.literal("Matrix"))) }                                                            | 'MATCH (n:Person) WHERE (n)-[:ACTED_IN]->(:Movie {title: $p0}) RETURN n'
        'connected'            | { q, r -> q.where(GraphCriteria.connected(r, "movies", cb.literal("Matrix"), 3)) }                                                         | 'MATCH (n:Person) WHERE (n)-[:ACTED_IN*1..3]->(:Movie {title: $p0}) RETURN n'
        'connected by any'     | { q, r -> q.where(GraphCriteria.connected(r, null, cb.literal(42L), 2)) }                                                                 | 'MATCH (n:Person) WHERE EXISTS { MATCH (n)-[*1..2]-(m0:Person) WHERE id(m0) = $p0 } RETURN n'
        'related and property' | { q, r -> q.where(cb.and(GraphCriteria.related(r, "movies"), cb.equal(r.get("name"), "Keanu"))) }                                        | 'MATCH (n:Person) WHERE ((n)-[:ACTED_IN]->() AND n.name = $p0) RETURN n'
        'dynamic property'     | { q, r -> q.where(cb.isTrue(GraphCriteria.dynamic(r, "goesInPatch", Boolean))) }                                                           | 'MATCH (n:Person) WHERE n.goesInPatch = true RETURN n'
        'dynamic comparison'   | { q, r -> q.where(cb.greaterThan(GraphCriteria.dynamic(r, "legs", Integer), 2)) }                                                          | 'MATCH (n:Person) WHERE n.legs > $p0 RETURN n'
        'dynamic selection'    | { q, r -> q.select(GraphCriteria.dynamic(r, "nick name", String)) }                                                                        | 'MATCH (n:Person) RETURN n.`nick name` AS `nick name`'
        'single conjunction'   | { q, r -> q.where(cb.and(cb.equal(r.get("name"), "a"))) }                                                                                  | 'MATCH (n:Person) WHERE n.name = $p0 RETURN n'
        'disjunction'          | { q, r -> q.where(cb.or(cb.equal(r.get("name"), "a"), cb.isTrue(r.get("active")))) }                                                       | 'MATCH (n:Person) WHERE (n.name = $p0 OR n.active = true) RETURN n'
        'negation'             | { q, r -> q.where(cb.not(cb.equal(r.get("name"), "a"))) }                                                                                  | 'MATCH (n:Person) WHERE NOT (n.name = $p0) RETURN n'
        'in expressions'       | { q, r -> q.where(r.get("name").in(cb.literal("a"), cb.literal("b"))) }                                                                    | 'MATCH (n:Person) WHERE n.name IN [$p0, $p1] RETURN n'
        'in values'            | { q, r -> q.where(r.get("status").in(Person.Status.ACTIVE, Person.Status.RETIRED)) }                                                       | 'MATCH (n:Person) WHERE n.status IN [$p0, $p1] RETURN n'
        'not in'               | { q, r -> q.where(cb.not(r.get("age").in(1, 2))) }                                                                                         | 'MATCH (n:Person) WHERE NOT (n.age IN [$p0, $p1]) RETURN n'
        'between'              | { q, r -> q.where(cb.between(r.get("age"), 18, 65)) }                                                                                      | 'MATCH (n:Person) WHERE (n.age >= $p0 AND n.age <= $p1) RETURN n'
        'not between'          | { q, r -> q.where(cb.not(cb.between(r.get("age"), 18, 65))) }                                                                              | 'MATCH (n:Person) WHERE NOT ((n.age >= $p0 AND n.age <= $p1)) RETURN n'
        'is false'             | { q, r -> q.where(cb.isFalse(r.get("active"))) }                                                                                           | 'MATCH (n:Person) WHERE n.active = false RETURN n'
        'boolean property'     | { q, r -> q.where(cb.and(r.get("active"), cb.equal(r.get("name"), "a"))) }                                                                 | 'MATCH (n:Person) WHERE (n.active = true AND n.name = $p0) RETURN n'
        'id equals'            | { q, r -> q.where(cb.equal(r.id(), 1L)) }                                                                                                  | 'MATCH (n:Person) WHERE id(n) = $p0 RETURN n'
        'association equals'   | { q, r -> q.where(cb.equal(r.get("movies"), "Matrix")) }                                                                                   | 'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) WHERE a0.title = $p0 WITH DISTINCT n RETURN n'
        'fetch join'           | { q, r -> r.join("movies", Join.Type.FETCH); q.select(r) }                                                                                 | 'MATCH (n:Person) OPTIONAL MATCH (n)-[:ACTED_IN]->(j0:Movie) WITH n, collect(j0) AS j_movies RETURN n, j_movies'
    }

    void "test update and delete with a relationship criterion"() {
        expect:
        update { u, r -> u.set("name", "Neo").where(cb.equal(r.join("movies").get("title"), "Matrix")) } ==
            'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) WHERE a0.title = $p0 WITH DISTINCT n SET n.name = $p1 RETURN count(*)'
        delete { d, r -> d.where(cb.equal(r.join("movies").get("title"), "Matrix")) } ==
            'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) WHERE a0.title = $p0 WITH DISTINCT n DETACH DELETE n RETURN count(*)'
    }

    void "test updating a missing property fails"() {
        when:
        update { u, r -> u.set("missing", "x") }

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot update non-existent property: missing'
    }

    void "test LIKE and subqueries are rejected"() {
        when:
        select { q, r -> q.where(cb.like(r.get("name"), "K%")) }

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.startsWith('LIKE is not supported by graph repositories')

        when:
        select { q, r ->
            def subquery = q.subquery(String)
            subquery.select(subquery.from(Movie).get("title"))
            q.where(cb.exists(subquery))
        }

        then:
        e = thrown(UnsupportedOperationException)
        e.message == 'Subqueries are not supported by graph repositories'
    }

    void "test literals"() {
        given:
        def builder = new CypherQueryBuilder()

        expect:
        builder.asLiteral(null) == 'null'
        builder.asLiteral(42) == '42'
        builder.asLiteral(true) == 'true'
        builder.asLiteral(Person.Status.ACTIVE) == "'ACTIVE'"
        builder.asLiteral(["a", 1]) == "['a', 1]"
        builder.asLiteral("it's a \\ path") == "'it\\'s a \\\\ path'"
        builder.buildLimitAndOffset(0, 0) == ''
        builder.buildInsert(AnnotationMetadata.EMPTY_METADATA, null) == null
    }

    void "test versioned writes match the version the entity was read with"() {
        given:
        def note = new TestEntityRegistry().getEntity(Note)
        def builder = new CypherQueryBuilder()

        expect:
        builder.buildUpdateVersionedNodeQuery(note, ["code", "text", "version"]) ==
            'MATCH (n:Note) WHERE n.code = $id AND n.version = $version SET n.code = $v0, n.text = $v1, n.version = $v2 RETURN n.code AS id'
        builder.buildDeleteVersionedNodeQuery(note) ==
            'MATCH (n:Note) WHERE n.code = $id AND n.version = $version DETACH DELETE n RETURN count(*) AS result'
        new AgeCypherQueryBuilder().buildDeleteVersionedNodeQuery(note).contains('n.version = $version')

        when:
        builder.buildDeleteVersionedNodeQuery(new TestEntityRegistry().getEntity(Movie))

        then:
        thrown(IllegalArgumentException)
    }

    void "test index statements"() {
        given:
        def registry = new TestEntityRegistry()
        def note = registry.getEntity(Note)

        expect: "an assigned identifier is unique and declared indexes follow"
        new CypherQueryBuilder().buildCreateIndexStatements(note) == [
            'CREATE CONSTRAINT Note_code_unique IF NOT EXISTS FOR (n:Note) REQUIRE n.code IS UNIQUE',
            'CREATE INDEX Note_text_index IF NOT EXISTS FOR (n:Note) ON (n.text)'
        ]
        new AgeCypherQueryBuilder().buildCreateIndexStatements(note).drop(1) == [
            'CREATE UNIQUE INDEX IF NOT EXISTS "Note_code_unique" ON "$__graph"."Note" (ag_catalog.agtype_access_operator(properties, \'"code"\'::agtype))',
            'CREATE INDEX IF NOT EXISTS "Note_text_index" ON "$__graph"."Note" (ag_catalog.agtype_access_operator(properties, \'"text"\'::agtype))'
        ]
        new AgeCypherQueryBuilder().buildCreateIndexStatements(note)[0].contains('create_vlabel(\'$__graph\', \'Note\')')

        and: "a node with a generated identifier and no indexes has none"
        new CypherQueryBuilder().buildCreateIndexStatements(registry.getEntity(Person)).isEmpty()
        new AgeCypherQueryBuilder().buildCreateIndexStatements(registry.getEntity(Person)).isEmpty()
    }

    void "test batched node writes"() {
        given:
        def registry = new TestEntityRegistry()
        def builder = new CypherQueryBuilder()

        expect:
        builder.supportsBatchNodeWrites()
        new AgeCypherQueryBuilder().supportsBatchNodeWrites()
        new AgeCypherQueryBuilder().buildWriteNodesQuery(registry.getEntity(Note), ['code', 'text'], true, false)
            .contains('MERGE (n:Note {code: row.id}) SET n.code = row.properties.code, n.text = row.properties.text RETURN')
        builder.buildWriteNodesQuery(registry.getEntity(Person), ['name'], false, false) ==
            'UNWIND $rows AS row CREATE (n:Person) SET n += row.properties RETURN row.position AS position, id(n) AS id'
        builder.buildWriteNodesQuery(registry.getEntity(Person), ['name'], true, false) ==
            'UNWIND $rows AS row MATCH (n:Person) WHERE id(n) = row.id SET n += row.properties RETURN row.position AS position, id(n) AS id'
        builder.buildWriteNodesQuery(registry.getEntity(Note), ['code', 'text'], true, false) ==
            'UNWIND $rows AS row MERGE (n:Note {code: row.id}) SET n += row.properties RETURN row.position AS position, n.code AS id'
        builder.buildWriteNodesQuery(registry.getEntity(Note), ['code', 'text'], true, true) ==
            'UNWIND $rows AS row MATCH (n:Note) WHERE n.code = row.id AND n.version = row.version SET n += row.properties RETURN row.position AS position, n.code AS id'
    }

    private String select(Closure<?> definition) {
        def query = cb.createQuery(Person)
        PersistentEntityRoot root = query.from(Person)
        definition.call(query, root)
        return build(query).query
    }

    private String update(Closure<?> definition) {
        def query = cb.createCriteriaUpdate(Person)
        PersistentEntityRoot root = query.from(Person)
        definition.call(query, root)
        return build(query).query
    }

    private String delete(Closure<?> definition) {
        def query = cb.createCriteriaDelete(Person)
        PersistentEntityRoot root = query.from(Person)
        definition.call(query, root)
        return build(query).query
    }

    private QueryResult build(def query) {
        return ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new CypherQueryBuilder())
    }

    void "test graph criteria on Apache AGE #description"() {
        given:
        def query = cb.createQuery(Person)
        PersistentEntityRoot root = query.from(Person)
        definition.call(query, root)

        expect:
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new AgeCypherQueryBuilder()).query.contains(fragment)

        where:
        description  | definition                                                                         | fragment
        'related'    | { q, r -> q.where(GraphCriteria.related(r, "movies")) }                           | 'WHERE EXISTS((n)-[:ACTED_IN]->())'
        'related to' | { q, r -> q.where(GraphCriteria.relatedTo(r, "movies", cb.literal("Matrix"))) }  | 'WHERE EXISTS((n)-[:ACTED_IN]->(:Movie {title: $p0}))'
    }

    void "test graph criteria Apache AGE cannot render are rejected: #description"() {
        given:
        def query = cb.createQuery(Person)
        PersistentEntityRoot root = query.from(Person)
        definition.call(query, root)

        when:
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new AgeCypherQueryBuilder())

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(message)

        where:
        description             | definition                                                                      | message
        'a chain'               | { q, r -> q.where(GraphCriteria.connected(r, "movies", cb.literal("Matrix"), 3)) } | 'cannot test for a chain of relationships'
        'a database identifier' | { q, r -> q.where(GraphCriteria.connected(r, null, cb.literal(42L), 2)) }       | 'cannot match a related node by its database identifier'
    }

    void "test graph criteria check their arguments"() {
        given:
        PersistentEntityRoot root = cb.createQuery(Person).from(Person)

        when:
        GraphCriteria.connected(root, "movies", cb.literal("Matrix"), 0)

        then:
        def depth = thrown(IllegalArgumentException)
        depth.message.contains("at least 1")

        when:
        new RelatedPredicate<>(root, null, null, 1)

        then:
        def target = thrown(IllegalArgumentException)
        target.message.contains("needs a target node")

        when:
        GraphCriteria.related(Mock(Root), "movies")

        then:
        def plain = thrown(IllegalArgumentException)
        plain.message.contains("root of a Micronaut Data query")

        when:
        GraphCriteria.related(root, "movies").visitPredicate(Mock(PredicateVisitor))

        then:
        def predicate = thrown(UnsupportedOperationException)
        predicate.message.contains("graph repositories")

        when:
        GraphCriteria.dynamic(root, "legs", Integer).visitExpression(Mock(ExpressionVisitor))

        then:
        def expression = thrown(UnsupportedOperationException)
        expression.message.contains("graph repositories")

        expect:
        GraphCriteria.related(root, "movies").toString() == 'RelatedPredicate{relationship=movies, target=null, maxDepth=1}'
        GraphCriteria.dynamic(root, "legs", Integer).toString() == 'DynamicPropertyExpression{name=legs}'
        ((RelatedPredicate) GraphCriteria.related(root, "movies")).root == root
        ((DynamicPropertyExpression) GraphCriteria.dynamic(root, "legs", Integer)).root == root
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
