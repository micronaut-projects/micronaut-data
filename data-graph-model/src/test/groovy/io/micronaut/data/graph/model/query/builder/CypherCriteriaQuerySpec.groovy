package io.micronaut.data.graph.model.query.builder

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Join
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.graph.model.query.builder.entities.Movie
import io.micronaut.data.graph.model.query.builder.entities.Note
import io.micronaut.data.graph.model.query.builder.entities.Person
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.model.jpa.criteria.impl.DefaultOrder
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQueryBuilder
import io.micronaut.data.model.query.builder.QueryResult
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.Nulls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

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
        'joined projection'    | { q, r -> q.select(r.join("movies").get("title")) }                                                                                        | 'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) RETURN a0.title AS movies_title'
        'root and joined'      | { q, r -> q.multiselect(r.get("name"), r.join("movies").get("title"), r.join("movies").get("released")) }                                  | 'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) RETURN n.name AS name, a0.title AS movies_title, a0.released AS movies_released'
        'aliased joined'       | { q, r -> q.multiselect(r.get("name"), r.join("movies").get("title").alias("title")) }                                                    | 'MATCH (n:Person), (n)-[:ACTED_IN]->(a0:Movie) RETURN n.name AS name, a0.title AS title'
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
        builder.asLiteral(LocalDate.of(2024, 1, 31)) == "date('2024-01-31')"
        builder.asLiteral(LocalDateTime.of(2024, 1, 31, 10, 15, 30)) == "localdatetime('2024-01-31T10:15:30')"
        builder.asLiteral(LocalTime.of(10, 15, 30)) == "localtime('10:15:30')"
        builder.asLiteral(OffsetTime.of(10, 15, 30, 0, ZoneOffset.ofHours(1))) == "time('10:15:30+01:00')"
        builder.asLiteral(ZonedDateTime.of(2024, 1, 31, 10, 15, 30, 0, ZoneId.of("Europe/London"))) == "datetime('2024-01-31T10:15:30Z[Europe/London]')"
        builder.asLiteral(OffsetDateTime.of(2024, 1, 31, 10, 15, 30, 0, ZoneOffset.ofHours(2))) == "datetime('2024-01-31T10:15:30+02:00')"
        builder.asLiteral(Instant.parse("2024-01-31T10:15:30Z")) == "datetime('2024-01-31T10:15:30Z')"
        builder.asLiteral(Duration.ofMinutes(90)) == "duration('PT1H30M')"
        builder.asLiteral(Period.ofMonths(14)) == "duration('P14M')"
        builder.asLiteral(YearMonth.of(2024, 1)) == "'2024-01'"
        builder.asLiteral([LocalDate.of(2024, 1, 31)]) == "[date('2024-01-31')]"
        builder.asLiteral(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")) == "'123e4567-e89b-12d3-a456-426614174000'"
        builder.buildLimitAndOffset(0, 0) == ''
        builder.buildInsert(AnnotationMetadata.EMPTY_METADATA, null) == null
    }

    void "test versioned writes lock the node and then match the version the entity was read with"() {
        given:
        def note = new TestEntityRegistry().getEntity(Note)
        def builder = new CypherQueryBuilder()

        expect:
        builder.buildUpdateVersionedNodeQuery(note, ["code", "text", "version"]) ==
            'MATCH (n:Note) WHERE n.code = $id SET n.__lock = true REMOVE n.__lock WITH n WHERE n.version = $version SET n.code = $v0, n.text = $v1, n.version = $v2 RETURN n.code AS id'
        builder.buildDeleteVersionedNodeQuery(note) ==
            'MATCH (n:Note) WHERE n.code = $id SET n.__lock = true REMOVE n.__lock WITH n WHERE n.version = $version DETACH DELETE n RETURN count(*) AS result'
        new AgeCypherQueryBuilder().buildDeleteVersionedNodeQuery(note).contains('n.version = $version')

        when:
        builder.buildDeleteVersionedNodeQuery(new TestEntityRegistry().getEntity(Movie))

        then:
        thrown(IllegalArgumentException)
    }

    void "test an insert with an assigned identifier creates the node only when none has the identifier"() {
        given:
        def note = new TestEntityRegistry().getEntity(Note)

        expect:
        new CypherQueryBuilder().buildInsertNodeQuery(note, ["text", "version"]) ==
            'OPTIONAL MATCH (existing:Note) WHERE existing.code = $id WITH count(existing) AS found WHERE found = 0 CREATE (n:Note {code: $id}) SET n.text = $v0, n.version = $v1 RETURN n.code AS id'
        new AgeCypherQueryBuilder().buildInsertNodeQuery(note, ["text", "version"]).contains('CREATE (n:Note {code: $id})')
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
        !new AgeCypherQueryBuilder().supportsBatchNodeWrites()
        builder.buildWriteNodesQuery(registry.getEntity(Person), false, false) ==
            'UNWIND $rows AS row CREATE (n:Person) SET n += row.properties RETURN row.position AS position, id(n) AS id'
        builder.buildWriteNodesQuery(registry.getEntity(Person), true, false) ==
            'UNWIND $rows AS row MATCH (n:Person) WHERE id(n) = row.id SET n += row.properties RETURN row.position AS position, id(n) AS id'
        builder.buildWriteNodesQuery(registry.getEntity(Note), true, false) ==
            'UNWIND $rows AS row MERGE (n:Note {code: row.id}) SET n += row.properties RETURN row.position AS position, n.code AS id'
        builder.buildWriteNodesQuery(registry.getEntity(Note), true, true) ==
            'UNWIND $rows AS row MATCH (n:Note) WHERE n.code = row.id SET n.__lock = true REMOVE n.__lock' +
            ' WITH collect(CASE WHEN n.version = row.version THEN {node: n, row: row} END) AS matched WHERE size(matched) = size($rows)' +
            ' UNWIND matched AS current WITH current.node AS n, current.row AS row SET n += row.properties RETURN row.position AS position, n.code AS id'
        builder.buildInsertNodesQuery(registry.getEntity(Note)) ==
            'OPTIONAL MATCH (existing:Note) WHERE existing.code IN [row IN $rows | row.id] WITH count(existing) AS found WHERE found = 0' +
            ' UNWIND $rows AS row CREATE (n:Note {code: row.id}) SET n += row.properties RETURN row.position AS position, n.code AS id'
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
