package io.micronaut.data.graph.model.query.builder

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Join
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.graph.annotation.Direction
import io.micronaut.data.graph.annotation.PropertyGraph
import io.micronaut.data.graph.model.query.PathQueryDefinition
import io.micronaut.data.graph.model.criteria.GraphCriteria
import io.micronaut.data.graph.model.query.builder.entities.Chimera
import io.micronaut.data.graph.model.query.builder.entities.Person
import io.micronaut.data.graph.model.query.builder.entities.Pet
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

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Checks the PGQL and SQL/PGQ rendered for queries built with the criteria API and for path queries.
 */
class OracleGraphCriteriaQuerySpec extends Specification {

    @Shared
    TestEntityRegistry registry = new TestEntityRegistry()

    @Shared
    PersistentEntityCriteriaBuilder cb = new RuntimeCriteriaBuilder(registry)

    void "test #language select #description"() {
        expect:
        select(builder(language), definition) == query

        where:
        language | description | definition | query
        PropertyGraph.QueryLanguage.PGQL | 'where' | { q, r -> q.where(cb.and(cb.equal(r.get("name"), "a"), cb.greaterThan(r.get("age"), 3))) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g WHERE (n.name = ? AND n.age > ?)'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'where' | { q, r -> q.where(cb.and(cb.equal(r.get("name"), "a"), cb.greaterThan(r.get("age"), 3))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name = ? AND n.age > ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        PropertyGraph.QueryLanguage.PGQL | 'or' | { q, r -> q.where(cb.or(cb.isNull(r.get("name")), cb.not(cb.isTrue(r.get("active"))))) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g WHERE (n.name IS NULL OR NOT (n.active = TRUE))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'or' | { q, r -> q.where(cb.or(cb.isNull(r.get("name")), cb.not(cb.isTrue(r.get("active"))))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name IS NULL OR NOT (n.active = TRUE)) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        PropertyGraph.QueryLanguage.PGQL | 'inList' | { q, r -> q.where(r.get("name").in("a", "b")) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g WHERE (n.name IN (?, ?))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'inList' | { q, r -> q.where(r.get("name").in("a", "b")) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name IN (?, ?)) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        PropertyGraph.QueryLanguage.PGQL | 'between' | { q, r -> q.where(cb.between(r.get("age"), 1, 9)) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g WHERE ((n.age >= ? AND n.age <= ?))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'between' | { q, r -> q.where(cb.between(r.get("age"), 1, 9)) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE ((n.age >= ? AND n.age <= ?)) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        PropertyGraph.QueryLanguage.PGQL | 'join' | { q, r -> q.where(cb.equal(r.join("movies").get("title"), "Matrix")) } | '''SELECT DISTINCT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g, MATCH (n) -[:ACTED_IN]-> (a0:Movie) ON g WHERE (a0.title = ?)'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'join' | { q, r -> q.where(cb.equal(r.join("movies").get("title"), "Matrix")) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" IN (SELECT "f0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) WHERE (a0.title = ?) COLUMNS (n.id AS "f0")))'''
        PropertyGraph.QueryLanguage.PGQL | 'order' | { q, r -> q.orderBy(new DefaultOrder(r.get("name"), true, true), cb.desc(r.get("age"))) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g ORDER BY LOWER(n.name) ASC, n.age DESC'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'order' | { q, r -> q.orderBy(new DefaultOrder(r.get("name"), true, true), cb.desc(r.get("age"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) ORDER BY LOWER("n__name") ASC, "n__age" DESC'''
        PropertyGraph.QueryLanguage.PGQL | 'distinctProj' | { q, r -> q.select(r.get("name")).distinct(true) } | '''SELECT DISTINCT n.name AS "name" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'distinctProj' | { q, r -> q.select(r.get("name")).distinct(true) } | '''SELECT DISTINCT "c0" AS "name" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'count' | { q, r -> q.select(cb.count(r)) } | '''SELECT COUNT(*) AS "result" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'count' | { q, r -> q.select(cb.count(r)) } | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'countDistinct' | { q, r -> q.select(cb.countDistinct(r.get("name"))) } | '''SELECT COUNT(DISTINCT n.name) AS "result" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'countDistinct' | { q, r -> q.select(cb.countDistinct(r.get("name"))) } | '''SELECT COUNT(DISTINCT "c0") AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'max' | { q, r -> q.select(cb.max(r.get("age"))) } | '''SELECT MAX(n.age) AS "result" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'max' | { q, r -> q.select(cb.max(r.get("age"))) } | '''SELECT MAX("c0") AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.age AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'upper' | { q, r -> q.select(cb.upper(r.get("name"))) } | '''SELECT UPPER(n.name) AS "result" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'upper' | { q, r -> q.select(cb.upper(r.get("name"))) } | '''SELECT UPPER("c0") AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'sum' | { q, r -> q.select(cb.sum(r.get("age"), 1)) } | '''SELECT (n.age + ?) AS "result" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'sum' | { q, r -> q.select(cb.sum(r.get("age"), 1)) } | '''SELECT "c0" AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS ((n.age + ?) AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'fn' | { q, r -> q.select(cb.function("NVL", String, r.get("name"), cb.literal("x"))) } | '''SELECT NVL(n.name, ?) AS "result" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'fn' | { q, r -> q.select(cb.function("NVL", String, r.get("name"), cb.literal("x"))) } | '''SELECT "c0" AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (NVL(n.name, ?) AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'multi' | { q, r -> q.multiselect(r.get("name").alias("n"), r.get("age")) } | '''SELECT n.name AS "n", n.age AS "age" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'multi' | { q, r -> q.multiselect(r.get("name").alias("n"), r.get("age")) } | '''SELECT "c0" AS "n", "c1" AS "age" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0", n.age AS "c1"))'''
        PropertyGraph.QueryLanguage.PGQL | 'limit' | { q, r -> q.offset(2).limit(5) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g OFFSET 2 LIMIT 5'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'limit' | { q, r -> q.offset(2).limit(5) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) OFFSET 2 ROWS FETCH NEXT 5 ROWS ONLY'''
        PropertyGraph.QueryLanguage.PGQL | 'ignoreCase' | { q, r -> q.where(cb.equal(cb.lower(r.get("name")), "a")) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g WHERE (LOWER(n.name) = ?)'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'ignoreCase' | { q, r -> q.where(cb.equal(cb.lower(r.get("name")), "a")) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (LOWER(n.name) = ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        PropertyGraph.QueryLanguage.PGQL | 'notNull' | { q, r -> q.where(cb.isNotNull(r.get("tags"))) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g WHERE (n.tags IS NOT NULL)'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'notNull' | { q, r -> q.where(cb.isNotNull(r.get("tags"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.tags IS NOT NULL) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'like' | { q, r -> q.where(cb.like(r.get("name"), "a%")) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name LIKE ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        PropertyGraph.QueryLanguage.PGQL | 'innerJoin' | { q, r -> r.join("movies", Join.Type.INNER); q.select(r) } | '''SELECT DISTINCT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g, MATCH (n) -[:ACTED_IN]-> (a0:Movie) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'innerJoin' | { q, r -> r.join("movies", Join.Type.INNER); q.select(r) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" IN (SELECT "f0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "f0")))'''
        PropertyGraph.QueryLanguage.PGQL | 'innerJoinCount' | { q, r -> r.join("movies", Join.Type.INNER); q.select(cb.count(r)) } | '''SELECT COUNT(DISTINCT n.id) AS "result" FROM MATCH (n:Person) ON g, MATCH (n) -[:ACTED_IN]-> (a0:Movie) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'innerJoinCount' | { q, r -> r.join("movies", Join.Type.INNER); q.select(cb.count(r)) } | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "c0")) WHERE "c0" IN (SELECT "f0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "f0")))'''
        PropertyGraph.QueryLanguage.PGQL | 'innerJoinProjection' | { q, r -> r.join("movies", Join.Type.INNER); q.select(r.get("name")) } | '''SELECT n.name AS "name" FROM MATCH (n:Person) ON g, MATCH (n) -[:ACTED_IN]-> (a0:Movie) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'innerJoinProjection' | { q, r -> r.join("movies", Join.Type.INNER); q.select(r.get("name")) } | '''SELECT "c0" AS "name" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.name AS "c0", n.id AS "c1")) WHERE "c1" IN (SELECT "f0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "f0")))'''
        PropertyGraph.QueryLanguage.PGQL | 'leftJoinCount' | { q, r -> r.join("movies", Join.Type.LEFT); q.select(cb.count(r)) } | '''SELECT COUNT(*) AS "result" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.PGQL | 'leftJoin' | { q, r -> r.join("movies", Join.Type.LEFT); q.select(r) } | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'negated isNull relationship' | { q, r -> q.where(cb.not(cb.isNull(r.get("movies")))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" IN (SELECT "f0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) WHERE (NOT (a0.title IS NULL)) COLUMNS (n.id AS "f0")))'''
        PropertyGraph.QueryLanguage.PGQL | 'isNotNull relationship' | { q, r -> q.where(cb.isNotNull(r.get("movies"))) } | '''SELECT DISTINCT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g, MATCH (n) -[:ACTED_IN]-> (a0:Movie) ON g WHERE (a0.title IS NOT NULL)'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'isNull relationship' | { q, r -> q.where(cb.isNull(r.get("movies"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" NOT IN (SELECT "r0_0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "r0_0")))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'negated isNotNull relationship' | { q, r -> q.where(cb.not(cb.isNotNull(r.get("movies")))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" NOT IN (SELECT "r0_0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "r0_0")))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'isNull identifier' | { q, r -> q.where(cb.isNull(r.join("movies").get("title"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" NOT IN (SELECT "r0_0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "r0_0")))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'isEmpty identifier' | { q, r -> q.where(cb.isEmptyString(r.join("movies").get("title"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" NOT IN (SELECT "r0_0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "r0_0")))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'order by relationship property' | { q, r -> q.orderBy(cb.asc(r.join("movies").get("released"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) ORDER BY (SELECT MIN("o0_0") FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (a0.released AS "o0_0", n.id AS "o0_1")) WHERE "o0_1" = "n__ID_") ASC'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'order by bound relationship' | { q, r -> q.where(cb.equal(r.join("movies").get("title"), "a")).orderBy(cb.desc(r.join("movies").get("released"))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" IN (SELECT "f0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) WHERE (a0.title = ?) COLUMNS (n.id AS "f0"))) ORDER BY (SELECT MAX("o0_0") FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (a0.released AS "o0_0", n.id AS "o0_1")) WHERE "o0_1" = "n__ID_") DESC'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'isNull and name' | { q, r -> q.where(cb.and(cb.equal(r.get("name"), "a"), cb.isNull(r.get("movies")))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (n.name = ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" NOT IN (SELECT "r0_0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "r0_0")))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'join and isNull' | { q, r -> q.where(cb.and(cb.equal(r.join("movies").get("title"), "a"), cb.isNull(r.get("movies")))) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) WHERE "n__ID_" IN (SELECT "f0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) WHERE (a0.title = ?) COLUMNS (n.id AS "f0"))) AND "n__ID_" NOT IN (SELECT "r1_0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "r1_0")))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'isNull count' | { q, r -> q.select(cb.count(r)).where(cb.isNull(r.get("movies"))) } | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "c0")) WHERE "c0" NOT IN (SELECT "r0_0" FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (n.id AS "r0_0")))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'order by relationship ignoring case' | { q, r -> q.orderBy(new DefaultOrder(r.join("movies").get("title"), true, true)) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags")) ORDER BY (SELECT MIN(LOWER("o0_0")) FROM GRAPH_TABLE (g MATCH (n IS Person), (n) -[IS ACTED_IN]-> (a0 IS Movie) COLUMNS (a0.title AS "o0_0", n.id AS "o0_1")) WHERE "o0_1" = "n__ID_") ASC'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'cast' | { q, r -> q.where(cb.equal(r.get("age").cast(String), "3")) } | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE (CAST(n.age AS VARCHAR2(4000)) = ?) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'cast selection' | { q, r -> q.select(r.get("age").cast(String)) } | '''SELECT "c0" AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (CAST(n.age AS VARCHAR2(4000)) AS "c0"))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'current timestamp selection' | { q, r -> q.select(cb.currentTimestamp()) } | '''SELECT "c0" AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (CURRENT_TIMESTAMP AS "c0"))'''
    }

    void "test #language select #description fails"() {
        when:
        select(builder(language), definition)

        then:
        def e = thrown(Exception)
        e.class.simpleName == exception
        e.message == message

        where:
        language | description | definition | exception | message
        PropertyGraph.QueryLanguage.PGQL | 'literal' | { q, r -> q.select(cb.literal(true)).where(cb.equal(r.get("name"), "a")) } | 'IllegalStateException' | '''Parameter expression not supported in selection'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'literal' | { q, r -> q.select(cb.literal(true)).where(cb.equal(r.get("name"), "a")) } | 'IllegalStateException' | '''Parameter expression not supported in selection'''
        PropertyGraph.QueryLanguage.PGQL | 'fetch' | { q, r -> r.join("movies", Join.Type.FETCH); q.select(r) } | 'UnsupportedOperationException' | '''Fetching relationships with @Join is not supported for Oracle property graphs: movies'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'fetch' | { q, r -> r.join("movies", Join.Type.FETCH); q.select(r) } | 'UnsupportedOperationException' | '''Fetching relationships with @Join is not supported for Oracle property graphs: movies'''
        PropertyGraph.QueryLanguage.PGQL | 'like' | { q, r -> q.where(cb.like(r.get("name"), "a%")) } | 'UnsupportedOperationException' | '''PGQL has no LIKE: use exact matches, a regular expression with Regex, or @PropertyGraph(language = SQL_PGQ)'''
        PropertyGraph.QueryLanguage.PGQL | 'isNull relationship' | { q, r -> q.where(cb.isNull(r.get("movies"))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is not supported by PGQL on Oracle property graphs, as a subquery is not allowed in a graph pattern: use @PropertyGraph(language = SQL_PGQ): movies'''
        PropertyGraph.QueryLanguage.PGQL | 'negated isNotNull relationship' | { q, r -> q.where(cb.not(cb.isNotNull(r.get("movies")))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is not supported by PGQL on Oracle property graphs, as a subquery is not allowed in a graph pattern: use @PropertyGraph(language = SQL_PGQ): movies'''
        PropertyGraph.QueryLanguage.PGQL | 'isNull identifier' | { q, r -> q.where(cb.isNull(r.join("movies").get("title"))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is not supported by PGQL on Oracle property graphs, as a subquery is not allowed in a graph pattern: use @PropertyGraph(language = SQL_PGQ): movies.title'''
        PropertyGraph.QueryLanguage.PGQL | 'isEmpty identifier' | { q, r -> q.where(cb.isEmptyString(r.join("movies").get("title"))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is not supported by PGQL on Oracle property graphs, as a subquery is not allowed in a graph pattern: use @PropertyGraph(language = SQL_PGQ): movies.title'''
        PropertyGraph.QueryLanguage.PGQL | 'order by relationship property' | { q, r -> q.orderBy(cb.asc(r.join("movies").get("released"))) } | 'UnsupportedOperationException' | '''Sorting on a relationship is only supported for Oracle property graphs on a property of the relationship with SQL/PGQ, as PGQL has no OPTIONAL MATCH and would drop the nodes without the relationship: movies.released'''
        PropertyGraph.QueryLanguage.PGQL | 'order by bound relationship' | { q, r -> q.where(cb.equal(r.join("movies").get("title"), "a")).orderBy(cb.desc(r.join("movies").get("released"))) } | 'UnsupportedOperationException' | '''Sorting on a relationship is only supported for Oracle property graphs on a property of the relationship with SQL/PGQ, as PGQL has no OPTIONAL MATCH and would drop the nodes without the relationship: movies.released'''
        PropertyGraph.QueryLanguage.PGQL | 'order by lower relationship' | { q, r -> q.orderBy(cb.asc(cb.lower(r.join("movies").get("title")))) } | 'UnsupportedOperationException' | '''Sorting on a relationship is only supported for Oracle property graphs on a property of the relationship with SQL/PGQ, as PGQL has no OPTIONAL MATCH and would drop the nodes without the relationship: movies.title'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'order by lower relationship' | { q, r -> q.orderBy(cb.asc(cb.lower(r.join("movies").get("title")))) } | 'UnsupportedOperationException' | '''Sorting on a relationship is only supported for Oracle property graphs on a property of the relationship with SQL/PGQ, as PGQL has no OPTIONAL MATCH and would drop the nodes without the relationship: movies.title'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'related' | { q, r -> q.where(GraphCriteria.related(r, "movies")) } | 'UnsupportedOperationException' | '''Relationship criteria are not supported for Oracle property graphs: use a finder on the relationship property'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'dynamic without attributes' | { q, r -> q.where(cb.isTrue(GraphCriteria.dynamic(r, "active", Boolean))) } | 'IllegalArgumentException' | '''Entity [io.micronaut.data.graph.model.query.builder.entities.Person] has no @DynamicAttributes property to read [active] from'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'isNull relationship in or' | { q, r -> q.where(cb.or(cb.equal(r.get("name"), "a"), cb.isNull(r.get("movies")))) } | 'UnsupportedOperationException' | '''Testing that a relationship is absent is only supported for Oracle property graphs on a relationship of the queried node, in a condition every result meets, not inside OR or NOT: movies'''
        PropertyGraph.QueryLanguage.PGQL | 'cast' | { q, r -> q.where(cb.equal(r.get("age").cast(String), "3")) } | 'UnsupportedOperationException' | '''CAST is not supported by PGQL on Oracle property graphs: use @PropertyGraph(language = SQL_PGQ)'''
        PropertyGraph.QueryLanguage.PGQL | 'current timestamp selection' | { q, r -> q.select(cb.currentTimestamp()) } | 'UnsupportedOperationException' | '''Selecting the current time is not supported by PGQL on Oracle property graphs: use @PropertyGraph(language = SQL_PGQ)'''
    }

    void "test #language dynamic properties read the JSON column"() {
        given:
        def query = cb.createQuery(Pet)
        PersistentEntityRoot root = query.from(Pet)
        query.where(cb.greaterThan(GraphCriteria.dynamic(root, "legs", Integer), 2))

        expect:
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, builder(language)).query.contains(fragment)

        where:
        language                             | fragment
        PropertyGraph.QueryLanguage.PGQL    | "WHERE (JSON_VALUE(n.attributes, '\$.\"legs\"' RETURNING NUMBER) > ?)"
        PropertyGraph.QueryLanguage.SQL_PGQ | "WHERE (JSON_VALUE(n.attributes, '\$.\"legs\"' RETURNING NUMBER) > ?)"
    }

    void "test #language selecting a dynamic property reads the JSON column"() {
        given:
        def query = cb.createQuery(Integer)
        PersistentEntityRoot root = query.from(Pet)
        query.select(GraphCriteria.dynamic(root, "legs", Integer))

        expect:
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, builder(language)).query == expected

        where:
        language                            | expected
        PropertyGraph.QueryLanguage.PGQL    | '''SELECT JSON_VALUE(n.attributes, '$."legs"' RETURNING NUMBER) AS "legs" FROM MATCH (n:Pet) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | '''SELECT "c0" AS "legs" FROM GRAPH_TABLE (g MATCH (n IS Pet) COLUMNS (JSON_VALUE(n.attributes, '$."legs"' RETURNING NUMBER) AS "c0"))'''
    }

    void "test dynamic property of type #type.simpleName reads the JSON value as #returning"() {
        given:
        def query = cb.createQuery(Pet)
        PersistentEntityRoot root = query.from(Pet)
        query.where(cb.isNotNull(GraphCriteria.dynamic(root, "value", type)))

        expect:
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, builder(PropertyGraph.QueryLanguage.SQL_PGQ)).query
            .contains("WHERE (JSON_VALUE(n.attributes, '\$.\"value\"'" + returning + ") IS NOT NULL)")

        where:
        type           | returning
        Integer        | ' RETURNING NUMBER'
        int            | ' RETURNING NUMBER'
        Long           | ' RETURNING NUMBER'
        Double         | ' RETURNING NUMBER'
        BigDecimal     | ' RETURNING NUMBER'
        Boolean        | ' RETURNING BOOLEAN'
        boolean        | ' RETURNING BOOLEAN'
        LocalDate      | ' RETURNING DATE'
        LocalDateTime  | ' RETURNING TIMESTAMP'
        OffsetDateTime | ' RETURNING TIMESTAMP WITH TIME ZONE'
        ZonedDateTime  | ' RETURNING TIMESTAMP WITH TIME ZONE'
        Instant        | ' RETURNING TIMESTAMP WITH TIME ZONE'
        String         | ''
        Object         | ''
    }

    void "test dynamic property names are quoted keys of the JSON path: #name"() {
        given:
        def query = cb.createQuery(Pet)
        PersistentEntityRoot root = query.from(Pet)
        query.where(cb.equal(GraphCriteria.dynamic(root, name, String), "x"))

        expect:
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, builder(PropertyGraph.QueryLanguage.SQL_PGQ)).query
            .contains("WHERE (JSON_VALUE(n.attributes, " + path + ") = ?)")

        where:
        name          | path
        'first.name'  | $/'$."first.name"'/$
        'nick name'   | $/'$."nick name"'/$
        "it's"        | $/'$."it''s"'/$
    }

    void "test dynamic property names a JSON path would need to escape are rejected: #name"() {
        given:
        def query = cb.createQuery(Pet)
        PersistentEntityRoot root = query.from(Pet)
        query.where(cb.equal(GraphCriteria.dynamic(root, name, String), "x"))

        when:
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, builder(language))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Schemaless property [" + name + "] cannot be read from an Oracle property graph: its name holds a double quote or a backslash, which a JSON path would need to escape"

        where:
        language                            | name
        PropertyGraph.QueryLanguage.SQL_PGQ | 'say "hi"'
        PropertyGraph.QueryLanguage.PGQL    | 'back\\slash'
    }

    void "test a dynamic property of an entity with two attribute maps is rejected"() {
        given:
        def query = cb.createQuery(Chimera)
        PersistentEntityRoot root = query.from(Chimera)
        query.where(cb.isNotNull(GraphCriteria.dynamic(root, "legs", Integer)))

        when:
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, builder(PropertyGraph.QueryLanguage.SQL_PGQ))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Entity [io.micronaut.data.graph.model.query.builder.entities.Chimera] has more than one @DynamicAttributes property: [attributes] and [extra]'
    }

    void "test #language path query #description"() {
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
            ? failure(builder(language), definitions[description]) == expected.substring(1)
            : builder(language).buildPathQuery(definitions[description]).query == expected

        where:
        language | description | expected
        PropertyGraph.QueryLanguage.PGQL | 'rel' | '''SELECT s.id AS "s__ID_", s.id AS "s__id", 'Person' AS "s__LABEL_", s.name AS "s__name", s.age AS "s__age", s.active AS "s__active", s.status AS "s__status", s.tags AS "s__tags", t.id AS "t__ID_", t.id AS "t__id", 'Person' AS "t__LABEL_", t.name AS "t__name", t.age AS "t__age", t.active AS "t__active", t.status AS "t__status", t.tags AS "t__tags", 'ACTED_IN' AS "r__TYPE_", CASE WHEN s IS SOURCE OF r THEN 1 ELSE 0 END AS "r__FORWARD_" FROM MATCH (s:Person) -[r:ACTED_IN]- (t:Person) ON g WHERE s.id = ? AND t.id = ?'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'rel' | '''SELECT * FROM GRAPH_TABLE (g MATCH (s IS Person) -[r IS ACTED_IN]- (t IS Person) WHERE s.id = ? AND t.id = ? COLUMNS (s.id AS "s__ID_", s.id AS "s__id", 'Person' AS "s__LABEL_", s.name AS "s__name", s.age AS "s__age", s.active AS "s__active", s.status AS "s__status", s.tags AS "s__tags", t.id AS "t__ID_", t.id AS "t__id", 'Person' AS "t__LABEL_", t.name AS "t__name", t.age AS "t__age", t.active AS "t__active", t.status AS "t__status", t.tags AS "t__tags", 'ACTED_IN' AS "r__TYPE_", CASE WHEN s IS SOURCE OF r THEN 1 ELSE 0 END AS "r__FORWARD_"))'''
        PropertyGraph.QueryLanguage.PGQL | 'relCount' | '''SELECT COUNT(*) AS "result" FROM MATCH (s:Person) -[r:ACTED_IN]-> (t:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'relCount' | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) -[r IS ACTED_IN]-> (t IS Person) COLUMNS (s.id AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'relExists' | '''SELECT 1 AS "result" FROM MATCH (s:Person) <-[r:ACTED_IN]- (t:Person) ON g LIMIT 1'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'relExists' | '''SELECT 1 AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) <-[r IS ACTED_IN]- (t IS Person) COLUMNS (s.id AS "c0")) FETCH NEXT 1 ROWS ONLY'''
        PropertyGraph.QueryLanguage.PGQL | 'relTwo' | '''!IllegalArgumentException: Relationship finders on Oracle property graphs need exactly one relationship type, for example findRelationshipsByFriends'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'relTwo' | '''!IllegalArgumentException: Relationship finders on Oracle property graphs need exactly one relationship type, for example findRelationshipsByFriends'''
        PropertyGraph.QueryLanguage.PGQL | 'shortest' | '''SELECT MATCHNUM(v1) AS "p__M_", ELEMENT_NUMBER(x) AS "p__I_", v1.id AS "v1__ID_", v1.id AS "v1__id", 'Person' AS "v1__LABEL_", v1.name AS "v1__name", v1.age AS "v1__age", v1.active AS "v1__active", v1.status AS "v1__status", v1.tags AS "v1__tags", v2.id AS "v2__ID_", v2.id AS "v2__id", 'Person' AS "v2__LABEL_", v2.name AS "v2__name", v2.age AS "v2__age", v2.active AS "v2__active", v2.status AS "v2__status", v2.tags AS "v2__tags", 'ACTED_IN' AS "x__TYPE_" FROM MATCH ANY SHORTEST (s:Person) -[x:ACTED_IN]->{1,5} (t:Person) ON g ONE ROW PER STEP (v1, x, v2) ORDER BY MATCHNUM(v1), ELEMENT_NUMBER(x)'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'shortest' | '''!IllegalArgumentException: Shortest paths are not supported by the SQL/PGQ dialect, which has no shortest path search: return every path up to the depth with findAllPaths'''
        PropertyGraph.QueryLanguage.PGQL | 'anyShortest' | '''SELECT MATCHNUM(v1) AS "p__M_", ELEMENT_NUMBER(x) AS "p__I_", v1.id AS "v1__ID_", v1.id AS "v1__id", 'Person' AS "v1__LABEL_", v1.name AS "v1__name", v1.age AS "v1__age", v1.active AS "v1__active", v1.status AS "v1__status", v1.tags AS "v1__tags", v2.id AS "v2__ID_", v2.id AS "v2__id", 'Person' AS "v2__LABEL_", v2.name AS "v2__name", v2.age AS "v2__age", v2.active AS "v2__active", v2.status AS "v2__status", v2.tags AS "v2__tags", LABEL(x) AS "x__TYPE_" FROM MATCH ANY SHORTEST (s:Person) -[x]->{1,$__pathLimit} (t:Person) ON g ONE ROW PER STEP (v1, x, v2) ORDER BY MATCHNUM(v1), ELEMENT_NUMBER(x)'''
        PropertyGraph.QueryLanguage.PGQL | 'allShortestCount' | '''!IllegalArgumentException: ALL SHORTEST is not supported by PGQL on Oracle property graphs'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'allShortestCount' | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) -[e]->{1,5} (t IS Person) WHERE s.id = ? AND t.id = ? AND s.id <> t.id AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0"))'''
        PropertyGraph.QueryLanguage.PGQL | 'allExists' | '''!IllegalArgumentException: PGQL path queries cannot bind parameters on Oracle property graphs: use @PropertyGraph(language = SQL_PGQ) to check for paths and count them'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'allExists' | '''SELECT 1 AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) -[e IS ACTED_IN]-{1,3} (t IS Person) WHERE s.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0")) FETCH NEXT 1 ROWS ONLY'''
        PropertyGraph.QueryLanguage.PGQL | 'noDepth' | '''!IllegalArgumentException: Oracle property graph path queries need a maximum depth: annotate the method with @PathLimit(n), or add an int parameter annotated with @PathLimit'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'noDepth' | '''!IllegalArgumentException: Oracle property graph path queries need a maximum depth: annotate the method with @PathLimit(n), or add an int parameter annotated with @PathLimit'''
        PropertyGraph.QueryLanguage.PGQL | 'boundDepth' | '''SELECT COUNT(*) AS "result" FROM MATCH ALL (s:Person) -[:ACTED_IN]->{1,$__pathLimit} (t:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'boundDepth' | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (s IS Person) -[e IS ACTED_IN]->{1,$__pathLimit} (t IS Person) WHERE COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) COLUMNS (s.id AS "c0"))'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'allPaths' | '''SELECT * FROM GRAPH_TABLE (g MATCH (s IS Person) -[e IS ACTED_IN]->{1,3} (t IS Person) WHERE s.id = ? AND t.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) ONE ROW PER STEP (v1, x, v2) COLUMNS (MATCHNUM() AS "p__M_", ELEMENT_NUMBER(x) AS "p__I_", v1.id AS "v1__ID_", v1.id AS "v1__id", 'Person' AS "v1__LABEL_", v1.name AS "v1__name", v1.age AS "v1__age", v1.active AS "v1__active", v1.status AS "v1__status", v1.tags AS "v1__tags", v2.id AS "v2__ID_", v2.id AS "v2__id", 'Person' AS "v2__LABEL_", v2.name AS "v2__name", v2.age AS "v2__age", v2.active AS "v2__active", v2.status AS "v2__status", v2.tags AS "v2__tags", 'ACTED_IN' AS "x__TYPE_")) ORDER BY "p__M_", "p__I_"'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'allPathsTwo' | '''SELECT * FROM GRAPH_TABLE (g MATCH (s IS Person) -[e IS ACTED_IN]-{1,3} (t IS Person) WHERE s.id = ? AND COUNT(DISTINCT JSON_SERIALIZE(EDGE_ID(e))) = COUNT(JSON_SERIALIZE(EDGE_ID(e))) ONE ROW PER STEP (v1, x, v2) COLUMNS (MATCHNUM() AS "p__M_", ELEMENT_NUMBER(x) AS "p__I_", v1.id AS "v1__ID_", v1.id AS "v1__id", 'Person' AS "v1__LABEL_", v1.name AS "v1__name", v1.age AS "v1__age", v1.active AS "v1__active", v1.status AS "v1__status", v1.tags AS "v1__tags", v2.id AS "v2__ID_", v2.id AS "v2__id", 'Person' AS "v2__LABEL_", v2.name AS "v2__name", v2.age AS "v2__age", v2.active AS "v2__active", v2.status AS "v2__status", v2.tags AS "v2__tags", 'ACTED_IN' AS "x__TYPE_")) ORDER BY "p__M_", "p__I_"'''
        PropertyGraph.QueryLanguage.SQL_PGQ | 'allPathsAnyType' | '''!IllegalArgumentException: Paths over any relationship type are not supported by the SQL/PGQ dialect, which cannot read the label of an edge: name the relationships of the path'''
    }

    void "test #language entity queries"() {
        given:
        def person = registry.getEntity(Person)
        def b = builder(language)

        expect:
        b.buildFindByIdQuery(person) == findById
        b.buildFindAllQuery(person) == findAll
        b.buildCountAllQuery(person) == countAll

        where:
        language | findById | findAll | countAll
        PropertyGraph.QueryLanguage.PGQL | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g WHERE n.id = ?''' | '''SELECT n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags" FROM MATCH (n:Person) ON g''' | '''SELECT COUNT(*) AS "result" FROM MATCH (n:Person) ON g'''
        PropertyGraph.QueryLanguage.SQL_PGQ | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) WHERE n.id = ? COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))''' | '''SELECT * FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "n__ID_", n.id AS "n__id", 'Person' AS "n__LABEL_", n.name AS "n__name", n.age AS "n__age", n.active AS "n__active", n.status AS "n__status", n.tags AS "n__tags"))''' | '''SELECT COUNT(*) AS "result" FROM GRAPH_TABLE (g MATCH (n IS Person) COLUMNS (n.id AS "c0"))'''
    }

    private static String failure(OracleGraphQueryBuilder b, PathQueryDefinition definition) {
        try {
            b.buildPathQuery(definition)
            return "no exception"
        } catch (Exception e) {
            return e.class.simpleName + ": " + e.message
        }
    }

    private static OracleGraphQueryBuilder builder(PropertyGraph.QueryLanguage language) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(PropertyGraph.name, [value: "g", language: language])
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
