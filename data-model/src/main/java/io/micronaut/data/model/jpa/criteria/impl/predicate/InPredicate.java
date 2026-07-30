package io.micronaut.data.model.jpa.criteria.impl.predicate;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The IN predicate implementation.
 *
 * @param <T> The expression type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class InPredicate<T> extends AbstractPredicate implements CriteriaBuilder.In<T> {

    private final Expression<T> expression;
    private final List<Expression<?>> values;
    private final @Nullable CriteriaBuilder criteriaBuilder;

    public InPredicate(Expression<T> expression, CriteriaBuilder criteriaBuilder) {
        this(expression, Collections.emptyList(), criteriaBuilder);
    }

    public InPredicate(Expression<T> expression, Collection<Expression<?>> values, @Nullable CriteriaBuilder criteriaBuilder) {
        this.expression = expression;
        this.values = new ArrayList<>(values);
        this.criteriaBuilder = criteriaBuilder;
    }

    public List<Expression<?>> getValues() {
        return values;
    }

    @Override
    public Expression<T> getExpression() {
        return expression;
    }

    @Override
    public InPredicate<T> value(T value) {
        values.add(criteriaBuilder == null ? new LiteralExpression<>(value) : criteriaBuilder.literal(value));
        return this;
    }

    @Override
    public InPredicate<T> value(Expression<? extends T> value) {
        values.add(value);
        return this;
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "InPredicate{" +
            "value=" + expression +
            ", values=" + values +
            ", criteriaBuilder=" + criteriaBuilder +
            '}';
    }
}
