package io.micronaut.data.search.elasticsearch.memory

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQueryBuilder
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.search.document.query.DocumentSearchQueryBuilder
import jakarta.persistence.criteria.Expression
import spock.lang.Shared
import spock.lang.Specification

/**
 * A minimum score is a parameter of the whole request, so the document stores take it from a top-level
 * AND condition only.
 */
class DocumentScoreRestrictionSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder cb = new RuntimeCriteriaBuilder(Stub(RuntimeEntityRegistry) {
        getEntity(_) >> { Class type -> new RuntimePersistentEntity<Object>(type) }
    })

    private String build(Closure<jakarta.persistence.criteria.Predicate> where) {
        def query = cb.createQuery(Note)
        def root = query.from(Note)
        def parameter = cb.parameter(String, null, "coffee")
        List<Expression<?>> fields = [root.get("title")]
        def score = cb.searchScore(fields, parameter, MatchMode.ANY)
        query.where(where.call(cb.textMatch(fields, parameter, MatchMode.ANY), cb.greaterThanOrEqualTo(score, cb.literal(0.5d)), root))
        return ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new DocumentSearchQueryBuilder()).query
    }

    void "a score restriction joined with AND becomes the min_score"() {
        expect:
        build { match, minScore, root -> cb.and(match, minScore) }.endsWith('"min_score":{"$mn_qp":1}}')
    }

    void "a score restriction under OR or NOT is rejected"() {
        when:
        build(where)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("cannot be combined with OR or negated")

        where:
        where << [
                { match, minScore, root -> cb.or(match, minScore) },
                { match, minScore, root -> cb.and(match, cb.not(minScore)) },
        ]
    }
}
