/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.criteria;

import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.impl.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInValuesPredicate;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelPredicateVisitor;
import io.micronaut.data.model.query.QueryModel;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Variation of {@link QueryModelPredicateVisitor} that converts literal query values to query parameters.
 *
 * For the queries build during the compilation time we want to inline all literals and create one query but for
 * runtime the literal values needs to be set as parameters to prevent SQL injection.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
final class LiteralsAsParametersQueryModelPredicateVisitor extends QueryModelPredicateVisitor {

    private final AbstractCriteriaBuilder criteriaBuilder;

    public LiteralsAsParametersQueryModelPredicateVisitor(AbstractCriteriaBuilder criteriaBuilder, QueryModel qm) {
        super(qm);
        this.criteriaBuilder = criteriaBuilder;
    }

    @Override
    public void visit(PersistentPropertyBinaryPredicate<?> predicate) {
        Expression<?> expression = predicate.getExpression();
        if (expression instanceof LiteralExpression) {
            super.visit(new PersistentPropertyBinaryPredicate<>(predicate.getPropertyPath(),
                asParameter(expression),
                predicate.getOp()));
        } else {
            super.visit(predicate);
        }
    }

    @Override
    public void visit(PersistentPropertyBetweenPredicate<?> propertyBetweenPredicate) {
        if (!(propertyBetweenPredicate.getFrom() instanceof ParameterExpression)
            || !(propertyBetweenPredicate.getTo() instanceof ParameterExpression)) {
            super.visit(
                new PersistentPropertyBetweenPredicate<>(propertyBetweenPredicate.getPropertyPath(),
                    asParameter(propertyBetweenPredicate.getFrom()),
                    asParameter(propertyBetweenPredicate.getTo()))
            );
        } else {
            super.visit(propertyBetweenPredicate);
        }
    }

    @Override
    public void visit(PersistentPropertyInPredicate<?> inValues) {
        if (inValues.getValues().stream().allMatch(v -> v instanceof ParameterExpression)) {
            super.visit(inValues);
        } else {
            super.visit(new PersistentPropertyInPredicate<>(inValues.getPropertyPath(), asCollectionParameter(inValues.getValues())));
        }
    }

    @Override
    public void visit(PersistentPropertyInValuesPredicate<?> inValues) {
        if (inValues.getValues().stream().allMatch(v -> v instanceof ParameterExpression)) {
            super.visit(inValues);
        } else {
            super.visit(new PersistentPropertyInValuesPredicate<>(inValues.getPropertyPath(), asCollectionParameter(inValues.getValues())));
        }
    }

    @NotNull
    private ParameterExpression<?> asParameter(Object exp) {
        if (exp instanceof ParameterExpression) {
            return (ParameterExpression<?>) exp;
        }
        Objects.requireNonNull(exp);
        Class<Object> type;
        Object value;
        if (exp instanceof LiteralExpression) {
            LiteralExpression literalExpression = (LiteralExpression<?>) exp;
            type = literalExpression.getJavaType();
            value = literalExpression.getValue();
        } else if (exp instanceof Expression) {
            throw new IllegalStateException("Unexpected expression!");
        } else {
            type = (Class<Object>) exp.getClass();
            value = exp;
        }
        return criteriaBuilder.parameter(type, null, value);
    }

    @NotNull
    private Set<Expression<?>> asCollectionParameter(Collection<?> values) {
        List<LiteralExpression<Object>> literals = values.stream().map(v -> {
            if (v instanceof LiteralExpression) {
                return (LiteralExpression<Object>) v;
            }
            if (v instanceof Expression) {
                throw new IllegalStateException("Expected to have all literal values");
            }
            return new LiteralExpression<Object>(v);
        }).collect(Collectors.toList());
        List<Object> literalValues = literals.stream().map(LiteralExpression::getValue).collect(Collectors.toList());
        Class<Object> javaType = (Class<Object>) literals.iterator().next().getJavaType();
        return Collections.singleton(criteriaBuilder.parameter(javaType, null, literalValues));
    }

}
