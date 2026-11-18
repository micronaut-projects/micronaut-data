package io.micronaut.data.document.mongodb.search

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.Expression
import spock.lang.Shared
import spock.lang.Specification

/**
 * Criteria over an entity with a {@code @MongoSearchIndex}, which render a {@code $search} stage.
 */
class MongoSearchCriteriaSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder cb = new RuntimeCriteriaBuilder()

    private String build(Closure where) {
        PersistentEntityCriteriaQuery<Object> query = cb.createQuery()
        def root = query.from(AtlasBook)
        def parameter = cb.parameter(String, null, "coffee")
        List<Expression<?>> fields = [root.get("title"), root.get("description")]
        query.where(where.call(cb.textMatch(fields, parameter, MatchMode.ANY), root))
        return query.build(AnnotationMetadata.EMPTY_METADATA, new MongoQueryBuilder()).getQuery()
    }

    void "a search predicate renders a \$search stage"() {
        expect:
        build { search, root -> search }.startsWith('[{$search:')
    }

    void "a negated search predicate is rejected rather than dropping the negation"() {
        when:
        build(where)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("cannot be combined with OR or negated")

        where:
        where << [
                { search, root -> cb.not(search) },
                { search, root -> cb.not(cb.and(search, cb.equal(root.get("author"), "Jane"))) },
                { search, root -> cb.or(search, cb.equal(root.get("author"), "Jane")) }
        ]
    }
}
