package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.data.model.jpa.criteria.impl.expression.AbstractExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CriteriaInTest {

    private static AbstractExpression<Long> expression() {
        return new LiteralExpression<>(Long.class);
    }

    @Test
    void inObjectVarargsIsRejected() {
        // A plain value would be inlined into the query, it has to be bound by the criteria builder
        assertThrows(IllegalStateException.class, () -> expression().in((Object) 1L, 2L));
    }

    @Test
    void inCollectionIsRejected() {
        assertThrows(IllegalStateException.class, () -> expression().in(List.of(1L, 2L)));
    }

    @Test
    void inExpressionVarargsKeepsExpressions() {
        AbstractExpression<Long> expression = expression();
        LiteralExpression<Long> value = new LiteralExpression<>(5L);

        Predicate predicate = expression.in(value);

        assertInstanceOf(InPredicate.class, predicate);
        InPredicate<?> in = (InPredicate<?>) predicate;
        assertSame(expression, in.getExpression());
        assertEquals(List.of(value), in.getValues());
    }

    @Test
    void inNullExpressionVarargsIsRejected() {
        assertThrows(NullPointerException.class, () -> expression().in((Expression<?>[]) null));
    }

    @Test
    void inSubqueryExpressionIsWrappedAsSingleValue() {
        AbstractExpression<Long> expression = expression();
        Expression<Collection<?>> subquery = new LiteralExpression<>(Collections.<Object>emptyList());

        InPredicate<?> in = (InPredicate<?>) expression.in(subquery);

        assertEquals(List.of(subquery), in.getValues());
    }

    @Test
    void inNullSubqueryExpressionIsRejected() {
        assertThrows(NullPointerException.class, () -> expression().in((Expression<Collection<?>>) null));
    }

    @Test
    void inPredicateValueWithoutCriteriaBuilderIsRejected() {
        InPredicate<Long> in = new InPredicate<>(expression(), List.of(), null);

        assertThrows(IllegalStateException.class, () -> in.value(42L));
    }
}
