/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.SubqueryExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.UnaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateBinaryOp;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateUnaryOp;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpressionType;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CollectionJoin;
import jakarta.persistence.criteria.CompoundSelection;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireBoolExpression;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireBoolExpressions;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;

/**
 * Abstract {@link jakarta.persistence.criteria.CriteriaBuilder} implementation.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractCriteriaBuilder implements PersistentEntityCriteriaBuilder {

    @NotNull
    private Predicate predicate(Expression<?> x, Expression<?> y, PredicateBinaryOp op) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        return new BinaryPredicate(x, y, op);
    }

    @NotNull
    @NextMajorVersion("Require non null y")
    private Predicate predicate(Expression<?> x, Object y, PredicateBinaryOp op) {
        Objects.requireNonNull(x);
        return new BinaryPredicate(x, literal(y), op);
    }

    @NotNull
    private Predicate comparable(Expression<?> x, Expression<?> y, PredicateBinaryOp op) {
        return new BinaryPredicate(x, y, op);
    }

    @NotNull
    private Predicate comparable(Expression<?> x, Object y, PredicateBinaryOp op) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), op);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public PersistentEntityCriteriaQuery<Tuple> createTupleQuery() {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <Y> CompoundSelection<Y> construct(@NonNull Class<Y> resultClass, @NonNull Selection<?>... selections) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public CompoundSelection<Tuple> tuple(@NonNull Selection<?>... selections) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public CompoundSelection<Object[]> array(@NonNull Selection<?>... selections) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public Order asc(@NonNull Expression<?> x) {
        return new PersistentPropertyOrder<>(requireProperty(x), true);
    }

    @Override
    @NonNull
    public Order desc(@NonNull Expression<?> x) {
        return new PersistentPropertyOrder<>(requireProperty(x), false);
    }

    @Override
    @NonNull
    public <N extends Number> Expression<Double> avg(@NonNull Expression<N> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.AVG);
    }

    @Override
    @NonNull
    public <N extends Number> Expression<N> sum(@NonNull Expression<N> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.SUM);
    }

    @Override
    @NonNull
    public Expression<Long> sumAsLong(@NonNull Expression<Integer> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.SUM, Long.class);
    }

    @Override
    @NonNull
    public Expression<Double> sumAsDouble(@NonNull Expression<Float> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.SUM, Double.class);
    }

    @Override
    @NonNull
    public <N extends Number> Expression<N> max(@NonNull Expression<N> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.MAX);
    }

    @Override
    @NonNull
    public <N extends Number> Expression<N> min(@NonNull Expression<N> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.MIN);
    }

    @Override
    @NonNull
    public <X extends Comparable<? super X>> Expression<X> greatest(@NonNull Expression<X> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.MAX);
    }

    @Override
    @NonNull
    public <X extends Comparable<? super X>> Expression<X> least(@NonNull Expression<X> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.MIN);
    }

    @Override
    @NonNull
    public Expression<Long> count(@NonNull Expression<?> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.COUNT, Long.class);
    }

    @Override
    @NonNull
    public Expression<Long> countDistinct(@NonNull Expression<?> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.COUNT_DISTINCT, Long.class);
    }

    @Override
    @NonNull
    public Predicate exists(@NonNull Subquery<?> subquery) {
        return new ExistsSubqueryPredicate(CriteriaUtils.requirePersistentEntitySubquery(subquery));
    }

    @Override
    @NonNull
    public <Y> Expression<Y> all(@NonNull Subquery<Y> subquery) {
        return new SubqueryExpression<>(SubqueryExpression.Type.ALL, CriteriaUtils.requirePersistentEntitySubquery(subquery));
    }

    @Override
    @NonNull
    public <Y> Expression<Y> some(@NonNull Subquery<Y> subquery) {
        return new SubqueryExpression<>(SubqueryExpression.Type.SOME, CriteriaUtils.requirePersistentEntitySubquery(subquery));
    }

    @Override
    @NonNull
    public <Y> Expression<Y> any(@NonNull Subquery<Y> subquery) {
        return new SubqueryExpression<>(SubqueryExpression.Type.ANY, CriteriaUtils.requirePersistentEntitySubquery(subquery));
    }

    @Override
    @NonNull
    public Predicate and(@NonNull Expression<Boolean> x, @NonNull Expression<Boolean> y) {
        return new ConjunctionPredicate(List.of(requireBoolExpression(x), requireBoolExpression(y)));
    }

    @Override
    @NonNull
    public Predicate and(@NonNull Predicate... restrictions) {
        return and(List.of(restrictions));
    }

    @Override
    @NonNull
    public Predicate and(@NonNull Iterable<Predicate> restrictions) {
        return new ConjunctionPredicate(requireBoolExpressions(restrictions));
    }

    @Override
    @NonNull
    public Predicate isEmptyString(@NonNull Expression<String> expression) {
        return new UnaryPredicate(expression, PredicateUnaryOp.IS_EMPTY);
    }

    @Override
    @NonNull
    public Predicate isNotEmptyString(@NonNull Expression<String> expression) {
        return new UnaryPredicate(expression, PredicateUnaryOp.IS_NOT_EMPTY);
    }

    @Override
    @NonNull
    public Predicate ilike(@NonNull Expression<String> x, @NonNull Expression<String> pattern) {
        return new LikePredicate(x, pattern, null, false, true);
    }

    @Override
    public Predicate endingWithString(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.ENDS_WITH);
    }

    @Override
    @NonNull
    public Predicate startsWithString(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.STARTS_WITH);
    }

    @Override
    @NonNull
    public Predicate containsString(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.CONTAINS);
    }

    @Override
    public Predicate containsStringIgnoreCase(Expression<String> x, Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.CONTAINS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate equalStringIgnoreCase(@NonNull Expression<String> x, @NonNull String y) {
        return new BinaryPredicate(x, literal(y), PredicateBinaryOp.EQUALS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate equalStringIgnoreCase(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.EQUALS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate notEqualStringIgnoreCase(@NonNull Expression<String> x, @NonNull String y) {
        return new BinaryPredicate(x, literal(y), PredicateBinaryOp.NOT_EQUALS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate notEqualStringIgnoreCase(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.NOT_EQUALS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate startsWithStringIgnoreCase(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.STARTS_WITH_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate endingWithStringIgnoreCase(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.ENDS_WITH_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate or(@NonNull Expression<Boolean> x, @NonNull Expression<Boolean> y) {
        return new DisjunctionPredicate(List.of(requireBoolExpression(x), requireBoolExpression(y)));
    }

    @Override
    @NonNull
    public Predicate or(@NonNull Predicate... restrictions) {
        return or(List.of(restrictions));
    }

    @Override
    @NonNull
    public Predicate or(@NonNull Iterable<Predicate> restrictions) {
        return new DisjunctionPredicate(requireBoolExpressions(restrictions));
    }

    @Override
    @NonNull
    public Predicate not(@NonNull Expression<Boolean> restriction) {
        return new NegatedPredicate(requireBoolExpression(restriction));
    }

    @Override
    @NonNull
    public Predicate conjunction() {
        return new ConjunctionPredicate(Collections.emptyList());
    }

    @Override
    @NonNull
    public Predicate disjunction() {
        return new DisjunctionPredicate(Collections.emptyList());
    }

    @Override
    @NonNull
    public Predicate isTrue(@NonNull Expression<Boolean> x) {
        return new UnaryPredicate(x, PredicateUnaryOp.IS_TRUE);
    }

    @Override
    @NonNull
    public Predicate isFalse(@NonNull Expression<Boolean> x) {
        return new UnaryPredicate(x, PredicateUnaryOp.IS_FALSE);
    }

    @Override
    @NonNull
    public Predicate isNull(@NonNull Expression<?> x) {
        return new UnaryPredicate(x, PredicateUnaryOp.IS_NULL);
    }

    @Override
    @NonNull
    public Predicate isNotNull(@NonNull Expression<?> x) {
        return new UnaryPredicate(x, PredicateUnaryOp.IS_NON_NULL);
    }

    @Override
    @NonNull
    public Predicate equal(@NonNull Expression<?> x, @NonNull Expression<?> y) {
        return predicate(x, y, PredicateBinaryOp.EQUALS);
    }

    @Override
    @NonNull
    @NextMajorVersion("Don't allow null values")
    public Predicate equal(@NonNull Expression<?> x, @Nullable Object y) {
        return predicate(x, y, PredicateBinaryOp.EQUALS);
    }

    @Override
    @NonNull
    public Predicate notEqual(@NonNull Expression<?> x, @NonNull Expression<?> y) {
        return predicate(x, y, PredicateBinaryOp.NOT_EQUALS);
    }

    @Override
    @NonNull
    public Predicate notEqual(@NonNull Expression<?> x, @Nullable Object y) {
        return predicate(x, y, PredicateBinaryOp.NOT_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate greaterThan(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return comparable(x, y, PredicateBinaryOp.GREATER_THAN);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate greaterThan(@NonNull Expression<? extends Y> x, @NonNull Y y) {
        return comparable(x, y, PredicateBinaryOp.GREATER_THAN);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate greaterThanOrEqualTo(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return comparable(x, y, PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate greaterThanOrEqualTo(@NonNull Expression<? extends Y> x, @NonNull Y y) {
        return comparable(x, y, PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate lessThan(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return comparable(x, y, PredicateBinaryOp.LESS_THAN);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate lessThan(@NonNull Expression<? extends Y> x, @NonNull Y y) {
        return comparable(x, y, PredicateBinaryOp.LESS_THAN);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate lessThanOrEqualTo(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return comparable(x, y, PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate lessThanOrEqualTo(@NonNull Expression<? extends Y> x, Y y) {
        return comparable(x, y, PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate between(@NonNull Expression<? extends Y> v, @NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return new BetweenPredicate(v, x, y);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate between(@NonNull Expression<? extends Y> v, @NonNull Y x, @NonNull Y y) {
        return new BetweenPredicate(v, literal(Objects.requireNonNull(x)), literal(Objects.requireNonNull(y)));
    }

    @Override
    @NonNull
    public Predicate gt(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.GREATER_THAN);
    }

    @Override
    @NonNull
    public Predicate gt(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), PredicateBinaryOp.GREATER_THAN);
    }

    @Override
    @NonNull
    public Predicate ge(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public Predicate ge(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public Predicate lt(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.LESS_THAN);
    }

    @Override
    @NonNull
    public Predicate lt(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), PredicateBinaryOp.LESS_THAN);
    }

    @Override
    @NonNull
    public Predicate le(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public Predicate le(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> neg(@NonNull Expression<N> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> abs(@NonNull Expression<N> x) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public <N extends Number> Expression<N> sum(@NonNull Expression<? extends N> x, Expression<? extends N> y) {
        return new BinaryExpression<>(x, y, BinaryExpressionType.SUM, (Class<N>) Number.class);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> sum(@NonNull Expression<? extends N> x, @NonNull N y) {
        return new BinaryExpression<>(x, literal(y), BinaryExpressionType.SUM, (Class<N>) Number.class);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> sum(@NonNull N x, @NonNull Expression<? extends N> y) {
        return new BinaryExpression<>(literal(x), y, BinaryExpressionType.SUM, (Class<N>) Number.class);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> prod(@NonNull Expression<? extends N> x, @NonNull Expression<? extends N> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> prod(@NonNull Expression<? extends N> x, @NonNull N y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> prod(@NonNull N x, @NonNull Expression<? extends N> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> diff(@NonNull Expression<? extends N> x, @NonNull Expression<? extends N> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> diff(@NonNull Expression<? extends N> x, @NonNull N y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> diff(@NonNull N x, @NonNull Expression<? extends N> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Number> quot(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Number> quot(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Number> quot(@NonNull Number x, @NonNull Expression<? extends Number> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Integer> mod(@NonNull Expression<Integer> x, @NonNull Expression<Integer> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    public Expression<Integer> mod(@NonNull Expression<Integer> x, @NonNull Integer y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Integer> mod(@NonNull Integer x, @NonNull Expression<Integer> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Double> sqrt(@NonNull Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Long> toLong(@NonNull Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Integer> toInteger(@NonNull Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Float> toFloat(@NonNull Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Double> toDouble(@NonNull Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<BigDecimal> toBigDecimal(@NonNull Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<BigInteger> toBigInteger(@NonNull Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> toString(@NonNull Expression<Character> x) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public <T> Expression<T> literal(@Nullable T value) {
        return new LiteralExpression<>(value);
    }

    @Override
    @NonNull
    public <T> Expression<T> nullLiteral(@NonNull Class<T> x) {
        return new LiteralExpression<>(x);
    }

    @Override
    @NonNull
    public <T> ParameterExpression<T> parameter(@NonNull Class<T> paramClass) {
        return parameter(paramClass, null, null);
    }

    @Override
    public <T> ParameterExpression<T> parameter(@NonNull Class<T> paramClass, @NonNull String name) {
        return parameter(paramClass, name, null);
    }

    /**
     * Create a new parameter with possible constant value.
     *
     * @param paramClass The param class
     * @param name       The param name
     * @param value      The param value
     * @param <T>        The param type
     * @return the parameter expression
     */
    @NonNull
    public <T> ParameterExpression<T> parameter(@NonNull Class<T> paramClass, @Nullable String name, @Nullable Object value) {
        return new DefaultParameterExpression<>(paramClass, name, value);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <C extends Collection<?>> Predicate isEmpty(@NonNull Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <C extends Collection<?>> Predicate isNotEmpty(@NonNull Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <C extends Collection<?>> Expression<Integer> size(@NonNull Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <C extends Collection<?>> Expression<Integer> size(@NonNull C collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <E, C extends Collection<E>> Predicate isMember(@NonNull Expression<E> elem, @NonNull Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <E, C extends Collection<E>> Predicate isMember(@NonNull E elem, @NonNull Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <E, C extends Collection<E>> Predicate isNotMember(@NonNull Expression<E> elem, @NonNull Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <E, C extends Collection<E>> Predicate isNotMember(@NonNull E elem, @NonNull Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <V, M extends Map<?, V>> Expression<Collection<V>> values(@NonNull M map) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <K, M extends Map<K, ?>> Expression<Set<K>> keys(@NonNull M map) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public Predicate regex(@NonNull Expression<String> x, @NonNull Expression<String> pattern) {
        return new BinaryPredicate(x, pattern, PredicateBinaryOp.REGEX);
    }

    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull Expression<String> pattern) {
        return new LikePredicate(x, pattern, null, false);
    }

    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull String pattern) {
        return new LikePredicate(x, literal(pattern), null, false);
    }

    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull Expression<String> pattern, @NonNull Expression<Character> escapeChar) {
        return new LikePredicate(x, pattern, escapeChar, false);
    }

    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull Expression<String> pattern, char escapeChar) {
        return new LikePredicate(x, pattern, literal(escapeChar), false);
    }

    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull String pattern, @NonNull Expression<Character> escapeChar) {
        return new LikePredicate(x, literal(pattern), escapeChar, false);
    }

    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull String pattern, char escapeChar) {
        return new LikePredicate(x, literal(pattern), literal(escapeChar), false);
    }

    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull Expression<String> pattern) {
        return new LikePredicate(x, pattern, null, true);
    }

    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull String pattern) {
        return new LikePredicate(x, literal(pattern), null, true);
    }

    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull Expression<String> pattern, @NonNull Expression<Character> escapeChar) {
        return new LikePredicate(x, pattern, escapeChar, true);
    }

    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull Expression<String> pattern, char escapeChar) {
        return new LikePredicate(x, pattern, literal(escapeChar), true);
    }

    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull String pattern, @NonNull Expression<Character> escapeChar) {
        return new LikePredicate(x, literal(pattern), escapeChar, true);
    }

    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull String pattern, char escapeChar) {
        return new LikePredicate(x, literal(pattern), literal(escapeChar), true);
    }

    @Override
    @NonNull
    public Expression<String> concat(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new BinaryExpression<>(x, y, BinaryExpressionType.CONCAT, String.class);
    }

    @Override
    @NonNull
    public Expression<String> concat(@NonNull Expression<String> x, @NonNull String y) {
        return new BinaryExpression<>(x, literal(y), BinaryExpressionType.CONCAT, String.class);
    }

    @Override
    @NonNull
    public Expression<String> concat(@NonNull String x, @NonNull Expression<String> y) {
        return new BinaryExpression<>(literal(x), y, BinaryExpressionType.CONCAT, String.class);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> substring(@NonNull Expression<String> x, @NonNull Expression<Integer> from) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> substring(@NonNull Expression<String> x, int from) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> substring(@NonNull Expression<String> x, @NonNull Expression<Integer> from, @NonNull Expression<Integer> len) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> substring(@NonNull Expression<String> x, int from, int len) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> trim(@NonNull Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> trim(@NonNull Trimspec ts, @NonNull Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> trim(@NonNull Expression<Character> t, @NonNull Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> trim(@NonNull Trimspec ts, @NonNull Expression<Character> t, @NonNull Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> trim(char t, @NonNull Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> trim(@NonNull Trimspec ts, char t, @NonNull Expression<String> x) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public Expression<String> lower(@NonNull Expression<String> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.LOWER);
    }

    @Override
    @NonNull
    public Expression<String> upper(@NonNull Expression<String> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.UPPER);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Integer> length(@NonNull Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Integer> locate(@NonNull Expression<String> x, @NonNull Expression<String> pattern) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Integer> locate(@NonNull Expression<String> x, @NonNull String pattern) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Integer> locate(@NonNull Expression<String> x, @NonNull Expression<String> pattern, @NonNull Expression<Integer> from) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Integer> locate(@NonNull Expression<String> x, @NonNull String pattern, int from) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Date> currentDate() {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Timestamp> currentTimestamp() {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<Time> currentTime() {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public <T> In<T> in(Expression<? extends T> expression) {
        return new InPredicate<>((Expression) expression, this);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <Y> Expression<Y> coalesce(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <Y> Expression<Y> coalesce(@NonNull Expression<? extends Y> x, Y y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <Y> Expression<Y> nullif(@NonNull Expression<Y> x, @NonNull Expression<?> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <Y> Expression<Y> nullif(@NonNull Expression<Y> x, Y y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <T> Coalesce<T> coalesce() {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <C, R> SimpleCase<C, R> selectCase(@NonNull Expression<? extends C> expression) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <R> Case<R> selectCase() {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public <T> Expression<T> function(@NonNull String name, @NonNull Class<T> type, @NonNull Expression<?>... args) {
        return new FunctionExpression<>(Objects.requireNonNull(name), List.of(args), Objects.requireNonNull(type));
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <X, T, V extends T> Join<X, V> treat(@NonNull Join<X, T> join, @NonNull Class<V> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <X, T, E extends T> CollectionJoin<X, E> treat(@NonNull CollectionJoin<X, T> join, @NonNull Class<E> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <X, T, E extends T> SetJoin<X, E> treat(@NonNull SetJoin<X, T> join, @NonNull Class<E> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <X, T, E extends T> ListJoin<X, E> treat(@NonNull ListJoin<X, T> join, @NonNull Class<E> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <X, K, T, V extends T> MapJoin<X, K, V> treat(@NonNull MapJoin<X, K, T> join, @NonNull Class<V> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <X, T extends X> Path<T> treat(@NonNull Path<X> path, @NonNull Class<T> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <X, T extends X> Root<T> treat(@NonNull Root<X> root, @NonNull Class<T> type) {
        throw notSupportedOperation();
    }

    @Override
    public Predicate arrayContains(Expression<?> x, Expression<?> y) {
        return predicate(x, y, PredicateBinaryOp.ARRAY_CONTAINS);
    }

    @Override
    public Expression<LocalDate> localDate() {
        throw notSupportedOperation();
    }

    @Override
    public Expression<LocalDateTime> localDateTime() {
        throw notSupportedOperation();
    }

    @Override
    public Expression<LocalTime> localTime() {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Integer> sign(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    @Override
    public <N extends Number> Expression<N> ceiling(Expression<N> x) {
        throw notSupportedOperation();
    }

    @Override
    public <N extends Number> Expression<N> floor(Expression<N> x) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Double> exp(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Double> ln(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Double> power(Expression<? extends Number> x, Expression<? extends Number> y) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Double> power(Expression<? extends Number> x, Number y) {
        throw notSupportedOperation();
    }

    @Override
    public <T extends Number> Expression<T> round(Expression<T> x, Integer n) {
        throw notSupportedOperation();
    }

}
