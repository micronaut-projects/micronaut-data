/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.runtime.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.data.constraint.AtLeast;
import jakarta.data.constraint.AtMost;
import jakarta.data.constraint.Between;
import jakarta.data.constraint.Constraint;
import jakarta.data.constraint.EqualTo;
import jakarta.data.constraint.GreaterThan;
import jakarta.data.constraint.In;
import jakarta.data.constraint.LessThan;
import jakarta.data.constraint.Like;
import jakarta.data.constraint.NotBetween;
import jakarta.data.constraint.NotEqualTo;
import jakarta.data.constraint.NotIn;
import jakarta.data.constraint.NotLike;
import jakarta.data.constraint.NotNull;
import jakarta.data.constraint.Null;
import jakarta.data.expression.Expression;
import jakarta.data.metamodel.Attribute;
import jakarta.data.metamodel.NavigableAttribute;
import jakarta.data.restrict.BasicRestriction;
import jakarta.data.restrict.CompositeRestriction;
import jakarta.data.restrict.Restriction;
import jakarta.data.spi.expression.function.CurrentDate;
import jakarta.data.spi.expression.function.CurrentDateTime;
import jakarta.data.spi.expression.function.CurrentTime;
import jakarta.data.spi.expression.function.FunctionExpression;
import jakarta.data.spi.expression.function.NumericCast;
import jakarta.data.spi.expression.function.NumericOperatorExpression;
import jakarta.data.spi.expression.literal.Literal;
import jakarta.data.spi.expression.path.NavigablePath;
import jakarta.data.spi.expression.path.Path;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The converter between Jakarta Data's {@link Restriction} and Micronaut Data's {@link PredicateSpecification}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class JakartaDataRestrictionsConverter<T> implements TypeConverter<Restriction<T>, PredicateSpecification<T>> {

    private final DateTimeProvider<OffsetDateTime> dateTimeProvider;

    JakartaDataRestrictionsConverter(DateTimeProvider<OffsetDateTime> dateTimeProvider) {
        this.dateTimeProvider = dateTimeProvider;
    }

    @Override
    public Optional<PredicateSpecification<T>> convert(Restriction<T> restriction, Class<PredicateSpecification<T>> targetType, ConversionContext context) {
        if (!(context instanceof ArgumentConversionContext<?> argumentConversionContext)) {
            throw new IllegalArgumentException("ConversionContext must be an ArgumentConversionContext");
        }
        Optional<Argument<?>> rootEntityType = argumentConversionContext.getArgument().getFirstTypeVariable();
        if (rootEntityType.isEmpty()) {
            throw new IllegalArgumentException("Argument must have a generic type");
        }
        return Optional.of(
            (root, criteriaBuilder) -> {
                if (restriction == null) {
                    return null;
                }
                return JakartaDataRestrictionsConverter.this.toPredicate(root, criteriaBuilder, restriction);
            }

        );
    }

    private Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Restriction<?> restriction) {
        return toPredicateInternal(root, criteriaBuilder, restriction);
    }

    private <R> Predicate toPredicateInternal(Root<T> root, CriteriaBuilder criteriaBuilder, Restriction<R> restriction) {
        return switch (restriction) {
            case CompositeRestriction<R> compositeRestriction ->
                predicateFromCompositeRestriction(root, criteriaBuilder, compositeRestriction);
            case BasicRestriction<R, ?> basicRestriction ->
                predicateFromBasicRestriction(root, criteriaBuilder, basicRestriction);
            case null, default ->
                throw new IllegalStateException("Unknown Restriction: " + restriction + " of type " + restriction.getClass());
        };
    }

    private <R> Predicate predicateFromCompositeRestriction(Root<T> root, CriteriaBuilder criteriaBuilder, CompositeRestriction<R> compositeRestriction) {
        List<Restriction<? super R>> restrictions = compositeRestriction.restrictions();
        List<Predicate> predicates = new ArrayList<>(restrictions.size());
        for (Restriction<? super R> combinedRestriction : restrictions) {
            predicates.add(toPredicate(root, criteriaBuilder, combinedRestriction));
        }
        Predicate predicate;
        if (compositeRestriction.type() == CompositeRestriction.Type.ANY) {
            predicate = criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        } else {
            predicate = criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        }
        if (compositeRestriction.isNegated()) {
            return predicate.not();
        }
        return predicate;
    }

    private <V> Predicate predicateFromBasicRestriction(Root<T> root, CriteriaBuilder criteriaBuilder, BasicRestriction<?, V> basicRestriction) {
        return toPredicate(
            root,
            criteriaBuilder,
            basicRestriction.constraint(),
            asExpression(root, criteriaBuilder, basicRestriction.expression())
        );
    }

    public <V> Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Constraint<V> constraint, jakarta.persistence.criteria.Expression<V> expression) {
        return switch (constraint) {
            case EqualTo<V> equalTo ->
                criteriaBuilder.equal(expression, asExpression(root, criteriaBuilder, equalTo.expression()));
            case NotEqualTo<V> notEqualTo ->
                criteriaBuilder.notEqual(expression, asExpression(root, criteriaBuilder, notEqualTo.expression()));
            case GreaterThan<?> greaterThan ->
                criteriaBuilder.greaterThan((jakarta.persistence.criteria.Expression<? extends Comparable>) expression,
                    (jakarta.persistence.criteria.Expression<? extends Comparable>) this.asExpression(root, criteriaBuilder, greaterThan.bound()));
            case LessThan<?> lessThan ->
                criteriaBuilder.lessThan((jakarta.persistence.criteria.Expression<? extends Comparable>) expression,
                    (jakarta.persistence.criteria.Expression<? extends Comparable>) asExpression(root, criteriaBuilder, lessThan.bound()));
            case AtLeast<?> atLeast ->
                criteriaBuilder.greaterThanOrEqualTo((jakarta.persistence.criteria.Expression<? extends Comparable>) expression,
                    (jakarta.persistence.criteria.Expression<? extends Comparable>) asExpression(root, criteriaBuilder, atLeast.bound()));
            case AtMost<?> atMost ->
                criteriaBuilder.lessThanOrEqualTo((jakarta.persistence.criteria.Expression<? extends Comparable>) expression,
                    (jakarta.persistence.criteria.Expression<? extends Comparable>) asExpression(root, criteriaBuilder, atMost.bound()));
            case Between<?> between ->
                criteriaBuilder.between((jakarta.persistence.criteria.Expression<? extends Comparable>) expression,
                    (jakarta.persistence.criteria.Expression<? extends Comparable>) asExpression(root, criteriaBuilder, between.lowerBound()),
                    (jakarta.persistence.criteria.Expression<? extends Comparable>) asExpression(root, criteriaBuilder, between.upperBound()));
            case NotBetween<?> notBetween ->
                criteriaBuilder.not(criteriaBuilder.between((jakarta.persistence.criteria.Expression<? extends Comparable>) expression,
                    (jakarta.persistence.criteria.Expression<? extends Comparable>) asExpression(root, criteriaBuilder, notBetween.lowerBound()),
                    (jakarta.persistence.criteria.Expression<? extends Comparable>) asExpression(root, criteriaBuilder, notBetween.upperBound())));
            case In<V> in ->
                expression.in(asExpressionList(root, criteriaBuilder, in.expressions()));
            case NotIn<V> notIn ->
                criteriaBuilder.not(expression.in(asExpressionList(root, criteriaBuilder, notIn.expressions())));
            case Null<V> ignored -> criteriaBuilder.isNull(expression);
            case NotNull<V> ignored -> criteriaBuilder.isNotNull(expression);
            case Like like -> criteriaBuilder.like(
                (jakarta.persistence.criteria.Expression<String>) expression,
                asExpression(root, criteriaBuilder, like.pattern()),
                like.escape()
            );
            case NotLike notLike -> criteriaBuilder.not(criteriaBuilder.like(
                (jakarta.persistence.criteria.Expression<String>) expression,
                asExpression(root, criteriaBuilder, notLike.pattern()),
                notLike.escape()
            ));
            case null, default ->
                throw new IllegalStateException("Unknown constraint: " + constraint + " of type: " + (constraint == null ? "null" : constraint.getClass()));
        };
    }

    private <V> List<jakarta.persistence.criteria.Expression<V>> asExpressionList(Root<T> root,
                                                                                 CriteriaBuilder criteriaBuilder,
                                                                                 List<Expression<?, V>> expressions) {
        List<jakarta.persistence.criteria.Expression<V>> result = new ArrayList<>(expressions.size());
        for (Expression<?, V> expression : expressions) {
            result.add(asExpression(root, criteriaBuilder, expression));
        }
        return result;
    }

    /**
     * Converts a Jakarta Data expression into the equivalent criteria expression.
     *
     * @param root            The criteria root
     * @param criteriaBuilder The criteria builder
     * @param expression      The Jakarta Data expression
     * @param <V>             The expression value type
     * @return The criteria expression
     */
    <V> jakarta.persistence.criteria.Expression<V> toCriteriaExpression(Root<?> root,
                                                                       CriteriaBuilder criteriaBuilder,
                                                                       Expression<?, V> expression) {
        return asExpression(root, criteriaBuilder, expression);
    }

    private <V> jakarta.persistence.criteria.Expression<V> asExpression(Root<?> root,
                                                                        CriteriaBuilder criteriaBuilder,
                                                                        Expression<?, V> expression) {
        return switch (expression) {
            case Attribute<?> attribute -> root.get(attribute.name());
            case Literal<?> literal -> criteriaBuilder.literal(((Literal<V>) literal).value());
            case Path<?, ?> path -> {
                jakarta.persistence.criteria.Path<?> path1 = asPath(root, path);
                yield path1.get(path.attribute().name());
            }
            case CurrentDateTime<?> ignore ->
                (jakarta.persistence.criteria.Expression<V>) criteriaBuilder.literal(
                    dateTimeProvider.getNow().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                );
            case CurrentDate<?> ignore ->
                (jakarta.persistence.criteria.Expression<V>) criteriaBuilder.literal(
                    dateTimeProvider.getNow().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                );
            case CurrentTime<?> ignore ->
                (jakarta.persistence.criteria.Expression<V>) criteriaBuilder.literal(
                    dateTimeProvider.getNow().toInstant().atZone(ZoneId.systemDefault()).toLocalTime()
                );
            case FunctionExpression<?, ?> functionExpression ->
                (jakarta.persistence.criteria.Expression<V>) criteriaBuilder.function(
                    functionExpression.name(),
                    functionExpression.type(),
                    asExpressions(root, criteriaBuilder, functionExpression)
                );
            case NumericOperatorExpression<?, ?> numericOperatorExpression -> {
                jakarta.persistence.criteria.Expression<?> numericExpression = applyNumericOperator(root, criteriaBuilder, numericOperatorExpression);
                @SuppressWarnings("unchecked")
                jakarta.persistence.criteria.Expression<V> cast = (jakarta.persistence.criteria.Expression<V>) numericExpression;
                yield cast;
            }
            case NumericCast numericCast -> numericCast(root, criteriaBuilder, numericCast);
            case null, default ->
                throw new IllegalStateException("Unknown Expression: " + expression + " of type: " + expression.getClass());
        };
    }

    private <T, N extends Number & Comparable<N>> jakarta.persistence.criteria.Expression<N> numericCast(Root<?> root,
                                                                                                         CriteriaBuilder criteriaBuilder,
                                                                                                         NumericCast<T, N> numericCast) {
        jakarta.persistence.criteria.Expression<?> exp = asExpression(root, criteriaBuilder, numericCast.expression());
        return exp.cast(numericCast.type());
    }

    private jakarta.persistence.criteria.Expression<?>[] asExpressions(Root<?> root,
                                                                       CriteriaBuilder criteriaBuilder,
                                                                       FunctionExpression<?, ?> functionExpression) {
        List<? extends Expression<?, ?>> arguments = functionExpression.arguments();
        List<jakarta.persistence.criteria.Expression<?>> list = new ArrayList<>(arguments.size());
        for (Expression<?, ?> a : arguments) {
            jakarta.persistence.criteria.Expression<?> expression = asExpression(root, criteriaBuilder, a);
            list.add(expression);
        }
        return list.toArray(new jakarta.persistence.criteria.Expression[0]);
    }

    private <V> jakarta.persistence.criteria.Path<V> asPath(jakarta.persistence.criteria.Path<?> criteriaPath, Path<?, ?> path) {
        return switch (path.expression()) {
            case NavigableAttribute<?, ?> navigableAttribute -> criteriaPath.get(navigableAttribute.name());
            case NavigablePath<?, ?, ?> navigablePath -> asPath(criteriaPath, navigablePath);
            case null, default ->
                throw new IllegalStateException("Unknown Path: " + path + " of type: " + path.getClass());
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private jakarta.persistence.criteria.Expression<?> applyNumericOperator(Root<?> root,
                                                                            CriteriaBuilder criteriaBuilder,
                                                                            NumericOperatorExpression<?, ?> numericOperatorExpression) {
        jakarta.persistence.criteria.Expression<?> left = asExpression(root, criteriaBuilder, numericOperatorExpression.left());
        jakarta.persistence.criteria.Expression<?> right = asExpression(root, criteriaBuilder, numericOperatorExpression.right());
        return switch (numericOperatorExpression.operator()) {
            case PLUS -> criteriaBuilder.sum((jakarta.persistence.criteria.Expression) left, (jakarta.persistence.criteria.Expression) right);
            case MINUS -> criteriaBuilder.diff((jakarta.persistence.criteria.Expression) left, (jakarta.persistence.criteria.Expression) right);
            case TIMES -> criteriaBuilder.prod((jakarta.persistence.criteria.Expression) left, (jakarta.persistence.criteria.Expression) right);
            case DIVIDE -> criteriaBuilder.quot((jakarta.persistence.criteria.Expression) left, (jakarta.persistence.criteria.Expression) right);
        };
    }
}
