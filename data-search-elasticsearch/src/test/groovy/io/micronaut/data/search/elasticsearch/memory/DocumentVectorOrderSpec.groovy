package io.micronaut.data.search.elasticsearch.memory

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQueryBuilder
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.search.document.query.DocumentSearchQueryBuilder
import io.micronaut.data.search.tck.entities.Article
import spock.lang.Shared
import spock.lang.Specification

/**
 * Ordering by vector distance is a nearest-neighbour search among the documents the predicate selects.
 */
class DocumentVectorOrderSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder cb = new RuntimeCriteriaBuilder(Stub(RuntimeEntityRegistry) {
        getEntity(_) >> { Class type -> new RuntimePersistentEntity<Object>(type) }
    })

    void "the predicate of a query ordered by vector distance filters the knn search"() {
        given:
        def query = cb.createQuery(Article)
        def root = query.from(Article)
        query.where(cb.equal(root.get("title"), cb.literal("Coffee")))
        def distance = cb.function(VectorScoringDialectSupport.SCORE_FUNCTION, Double, root.get("embedding"), cb.literal(Vector.of([1f, 0f, 0f] as float[])))
        query.orderBy(cb.asc(distance))

        when:
        def json = ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new DocumentSearchQueryBuilder()).query

        then:
        json.startsWith('{"query":{"knn":{"field":"embedding",')
        json.contains('"filter":{"term":{"title.keyword":')
    }
}
