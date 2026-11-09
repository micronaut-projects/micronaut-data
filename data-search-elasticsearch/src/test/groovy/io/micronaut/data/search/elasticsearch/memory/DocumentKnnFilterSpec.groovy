package io.micronaut.data.search.elasticsearch.memory

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQueryBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.search.document.query.DocumentSearchQueryBuilder
import io.micronaut.data.search.tck.entities.Article
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import spock.lang.Shared
import spock.lang.Specification

/**
 * The other AND conditions of a vector search go into the filter of the knn query, however the conjunctions
 * are nested, and k and num_candidates stay within the limit of the stores.
 */
class DocumentKnnFilterSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder cb = new RuntimeCriteriaBuilder(Stub(RuntimeEntityRegistry) {
        getEntity(_) >> { Class type -> new RuntimePersistentEntity<Object>(type) }
    })

    private Map<String, Object> build(int limit, Closure<Predicate> where) {
        PersistentEntityCriteriaQuery<Article> query = cb.createQuery(Article)
        PersistentEntityRoot<Article> root = query.from(Article)
        query.where(where.call(root))
        if (limit > 0) {
            query.limit(limit)
        }
        String json = ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new DocumentSearchQueryBuilder()).query
        return io.micronaut.json.JsonMapper.createDefault().readValue(json, Map) as Map<String, Object>
    }

    private Predicate near(PersistentEntityRoot<Article> root) {
        def score = cb.function(VectorScoringDialectSupport.SCORE_FUNCTION, Double, root.get("embedding"), cb.literal(Vector.of([1f, 0f, 0f] as float[])))
        return cb.lessThanOrEqualTo(score, cb.literal(0.5d))
    }

    private Predicate title(PersistentEntityRoot<Article> root) {
        return cb.equal(root.get("title"), cb.literal("Coffee"))
    }

    private Predicate bodyIsTea(PersistentEntityRoot<Article> root) {
        return cb.equal(root.get("body"), cb.literal("Tea"))
    }

    private Predicate match(PersistentEntityRoot<Article> root) {
        return cb.textMatch([root.get("title")] as List<Expression<?>>, cb.literal("coffee"), MatchMode.ANY)
    }

    private Predicate minScore(PersistentEntityRoot<Article> root) {
        def score = cb.searchScore([root.get("title")] as List<Expression<?>>, cb.literal("coffee"), MatchMode.ANY)
        return cb.greaterThanOrEqualTo(score, cb.literal(0.5d))
    }

    private static List<Object> filterTerms(Map<String, Object> knn) {
        def filter = knn.filter as Map<String, Object>
        return filter.bool ? (filter.bool as Map).must as List<Object> : [filter]
    }

    void "a nested conjunction keeps the inner conditions in the knn filter"() {
        when:
        def request = build(0, condition)
        def knn = request.query.knn as Map<String, Object>

        then: "both conditions filter the knn search, none of them post-filters the top k"
        knn != null
        filterTerms(knn).collect { (it.term as Map).keySet() }.flatten() as Set == ["title.keyword", "body.keyword"] as Set

        where:
        condition << [
                { root -> cb.and(cb.and(title(root), near(root)), bodyIsTea(root)) },
                { root -> cb.and(bodyIsTea(root), cb.and(near(root), title(root))) },
                { root -> cb.and(cb.and(title(root), bodyIsTea(root)), near(root)) },
        ]
    }

    void "with a minimum score the conditions filter the knn search and stay scoring clauses"() {
        when:
        def request = build(0, condition)
        def must = (request.query.bool as Map).must as List<Map<String, Object>>
        def knn = must.find { it.knn }?.knn as Map<String, Object>

        then: "the min_score compares the same summed score, among the nearest neighbours matching the conditions"
        request.min_score != null
        knn != null
        filterTerms(knn).size() == 2
        filterTerms(knn).any { it.simple_query_string }
        filterTerms(knn).any { (it.term as Map)?.containsKey("body.keyword") }
        must.findAll { !it.knn } as Set == filterTerms(knn) as Set

        where:
        condition << [
                { root -> cb.and(match(root), minScore(root), near(root), bodyIsTea(root)) },
                { root -> cb.and(cb.and(match(root), near(root)), minScore(root), bodyIsTea(root)) },
        ]
    }

    void "k and num_candidates are capped at the limit of the stores"() {
        when:
        def knn = build(limit) { root -> near(root) }.query.knn as Map<String, Object>

        then:
        knn.k == k
        knn.num_candidates == candidates

        where:
        limit  | k      | candidates
        0      | 10     | 100
        2000   | 2000   | 10_000
        20_000 | 10_000 | 10_000
    }
}
