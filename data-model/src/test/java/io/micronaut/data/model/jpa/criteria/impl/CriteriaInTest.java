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

    private static List<?> literalValues(InPredicate<?> in) {
        return in.getValues().stream().map(v -> ((LiteralExpression<?>) v).getValue()).toList();
    }

    @Test
    void inObjectVarargsWrapsValuesAsLiterals() {
        AbstractExpression<Long> expression = expression();

        Predicate predicate = expression.in(1L, 2L);

        assertInstanceOf(InPredicate.class, predicate);
        InPredicate<?> in = (InPredicate<?>) predicate;
        assertSame(expression, in.getExpression());
        assertEquals(List.of(1L, 2L), literalValues(in));
    }

    @Test
    void inNullObjectVarargsIsRejected() {
        assertThrows(NullPointerException.class, () -> expression().in((Object[]) null));
    }

    @Test
    void inExpressionVarargsKeepsExpressions() {
        AbstractExpression<Long> expression = expression();
        LiteralExpression<Long> value = new LiteralExpression<>(5L);

        InPredicate<?> in = (InPredicate<?>) expression.in(value);

        assertEquals(List.of(value), in.getValues());
    }

    @Test
    void inCollectionWrapsLiteralsAndKeepsExpressions() {
        AbstractExpression<Long> expression = expression();
        LiteralExpression<Long> expressionValue = new LiteralExpression<>(5L);

        InPredicate<?> in = (InPredicate<?>) expression.in(List.of(1L, expressionValue));

        List<Expression<?>> values = in.getValues();
        assertEquals(2, values.size());
        assertEquals(1L, ((LiteralExpression<?>) values.get(0)).getValue());
        assertSame(expressionValue, values.get(1));
    }

    @Test
    void inNullCollectionIsRejected() {
        assertThrows(NullPointerException.class, () -> expression().in((Collection<?>) null));
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
    void inPredicateValueWithoutCriteriaBuilderWrapsAsLiteral() {
        AbstractExpression<Long> expression = expression();

        InPredicate<Long> in = new InPredicate<>(expression, List.of(), null);
        in.value(42L);

        assertEquals(List.of(42L), literalValues(in));
    }
}
