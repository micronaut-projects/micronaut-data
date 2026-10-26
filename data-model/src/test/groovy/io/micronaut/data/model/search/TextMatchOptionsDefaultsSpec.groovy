package io.micronaut.data.model.search

import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import spock.lang.Specification

/**
 * The option-taking overloads that criteria builders and predicate visitors inherit delegate to the
 * plain overload when no options are set, and reject options they cannot honour.
 */
class TextMatchOptionsDefaultsSpec extends Specification {

    void "a criteria builder without options support delegates the default options and rejects others"() {
        given:
        def builder = Mock(PersistentEntityCriteriaBuilder)
        builder.textMatch(_, _, _, _) >> { callRealMethod() }
        builder.searchScore(_, _, _, _) >> { callRealMethod() }
        def fields = [Mock(Expression)]
        def query = Mock(Expression)
        def predicate = Mock(Predicate)
        def score = Mock(Expression)

        when:
        def matched = builder.textMatch(fields, query, MatchMode.ANY, TextMatchOptions.NONE)
        def scored = builder.searchScore(fields, query, MatchMode.ANY, TextMatchOptions.NONE)

        then:
        1 * builder.textMatch(fields, query, MatchMode.ANY) >> predicate
        1 * builder.searchScore(fields, query, MatchMode.ANY) >> score
        matched.is(predicate)
        scored.is(score)

        when:
        builder.textMatch(fields, query, MatchMode.ANY, new TextMatchOptions([title: 2d], 0))

        then:
        thrown(UnsupportedOperationException)

        when:
        builder.searchScore(fields, query, MatchMode.FUZZY, new TextMatchOptions([:], 2))

        then:
        thrown(UnsupportedOperationException)
    }

    void "a predicate visitor without options support delegates the default options and rejects others"() {
        given:
        def visitor = Mock(AdvancedPredicateVisitor)
        visitor.visitTextMatch(_, _, _, _) >> { callRealMethod() }
        def fields = [Mock(Expression)]
        def query = Mock(Expression)

        when:
        visitor.visitTextMatch(fields, query, MatchMode.ALL, TextMatchOptions.NONE)

        then:
        1 * visitor.visitTextMatch(fields, query, MatchMode.ALL)

        when:
        visitor.visitTextMatch(fields, query, MatchMode.ALL, new TextMatchOptions([title: 2d], 0))

        then:
        thrown(UnsupportedOperationException)
    }
}
