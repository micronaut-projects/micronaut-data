package io.micronaut.data.model.jpa.criteria.impl.expression;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The abstract expression.
 *
 * @param <E> The expression type
 * @author Denis Stepanov
 * @since 4.9
 */
@Internal
public abstract class AbstractExpression<E> implements IExpression<E> {

    private final ExpressionType<E> expressionType;

    public AbstractExpression(ExpressionType<E> expressionType) {
        this.expressionType = expressionType;
    }

    @Override
    public Class<? extends E> getJavaType() {
        return expressionType.getJavaType();
    }

    @Override
    public final ExpressionType<E> getExpressionType() {
        return expressionType;
    }

    @Override
    public Predicate in(Object... values) {
        return in(Arrays.asList(Objects.requireNonNull(values)));
    }

    @Override
    public Predicate in(Expression<?>... values) {
        return new InPredicate<>(this, Arrays.asList(values), null);
    }

    @Override
    public Predicate in(Collection<?> values) {
        List<Expression<?>> expressions = Objects.requireNonNull(values).stream().map(value -> {
            if (value instanceof Expression<?> expression) {
                return expression;
            }
            return new LiteralExpression<>(value);
        }).toList();
        return new InPredicate<>(this, expressions, null);
    }

    @Override
    public Predicate in(Expression<Collection<?>> values) {
        return new InPredicate<>(this, List.of(Objects.requireNonNull(values)), null);
    }
}
