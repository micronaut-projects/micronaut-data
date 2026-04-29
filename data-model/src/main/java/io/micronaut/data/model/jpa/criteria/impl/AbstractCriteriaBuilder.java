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
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.expression.CurrentTemporalExpression;
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
import jakarta.persistence.criteria.CriteriaSelect;
import jakarta.persistence.criteria.CompoundSelection;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.TemporalField;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireBoolExpression;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireBoolExpressions;

/**
 * Abstract {@link jakarta.persistence.criteria.CriteriaBuilder} implementation.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractCriteriaBuilder implements PersistentEntityCriteriaBuilder {

    private final Supplier<OffsetDateTime> localDateTimeSupplier;

    protected AbstractCriteriaBuilder() {
        this(() -> {
            throw new IllegalStateException("Local date/time expressions require a DateTimeProvider.");
        });
    }

    protected AbstractCriteriaBuilder(Supplier<OffsetDateTime> localDateTimeSupplier) {
        this.localDateTimeSupplier = Objects.requireNonNull(localDateTimeSupplier);
    }

    @NotNull
    private Predicate predicate(Expression<?> x, Expression<?> y, PredicateBinaryOp op) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        return new BinaryPredicate(x, y, op);
    }

    @NotNull
    @NextMajorVersion("Require non null y")
    private Predicate predicate(Expression<?> x, @Nullable Object y, PredicateBinaryOp op) {
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

    public <Y> CompoundSelection<Y> construct(Class<Y> resultClass,  Selection<?>... selections) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public CompoundSelection<Tuple> tuple(Selection<?>... selections) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public CompoundSelection<Object[]> array(Selection<?>... selections) {
        throw notSupportedOperation();
    }

    @Override
    public CompoundSelection<Tuple> tuple(List<Selection<?>> selections) {
        throw notSupportedOperation();
    }

    @Override
    public CompoundSelection<Object[]> array(List<Selection<?>> selections) {
        throw notSupportedOperation();
    }

    @Override
    public <T> CriteriaSelect<T> union(CriteriaSelect<? extends T> left, CriteriaSelect<? extends T> right) {
        throw notSupportedOperation();
    }

    @Override
    public <T> CriteriaSelect<T> unionAll(CriteriaSelect<? extends T> left, CriteriaSelect<? extends T> right) {
        throw notSupportedOperation();
    }

    @Override
    public <T> CriteriaSelect<T> intersect(CriteriaSelect<? super T> left, CriteriaSelect<? super T> right) {
        throw notSupportedOperation();
    }

    @Override
    public <T> CriteriaSelect<T> intersectAll(CriteriaSelect<? super T> left, CriteriaSelect<? super T> right) {
        throw notSupportedOperation();
    }

    @Override
    public <T> CriteriaSelect<T> except(CriteriaSelect<T> left, CriteriaSelect<?> right) {
        throw notSupportedOperation();
    }

    @Override
    public <T> CriteriaSelect<T> exceptAll(CriteriaSelect<T> left, CriteriaSelect<?> right) {
        throw notSupportedOperation();
    }

    @Override

    public Order asc(Expression<?> x) {
        return sort(x, true, false);
    }

    @Override
    public Order asc(Expression<?> expression, Nulls nullPrecedence) {
        return new DefaultOrder<>(expression, true, false, nullPrecedence);
    }

    @Override

    public Order desc(Expression<?> x) {
        return sort(x, false, false);
    }

    @Override
    public Order desc(Expression<?> expression, Nulls nullPrecedence) {
        return new DefaultOrder<>(expression, false, false, nullPrecedence);
    }

    @Override
    public Order sort(Expression<?> x, boolean ascending, boolean ignoreCase) {
        return new DefaultOrder<>(x, ascending, ignoreCase);
    }

    @Override

    public <N extends Number> Expression<Double> avg(Expression<N> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.AVG);
    }

    @Override

    public <N extends Number> Expression<N> sum(Expression<N> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.SUM);
    }

    @Override

    public Expression<Long> sumAsLong(Expression<Integer> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.SUM, Long.class);
    }

    @Override

    public Expression<Double> sumAsDouble(Expression<Float> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.SUM, Double.class);
    }

    @Override

    public <N extends Number> Expression<N> max(Expression<N> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.MAX);
    }

    @Override

    public <N extends Number> Expression<N> min(Expression<N> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.MIN);
    }

    @Override

    public <X extends Comparable<? super X>> Expression<X> greatest(Expression<X> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.MAX);
    }

    @Override

    public <X extends Comparable<? super X>> Expression<X> least(Expression<X> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.MIN);
    }

    @Override

    public Expression<Long> count(Expression<?> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.COUNT, Long.class);
    }

    @Override

    public Expression<Long> countDistinct(Expression<?> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.COUNT_DISTINCT, Long.class);
    }

    @Override

    public Predicate exists(Subquery<?> subquery) {
        return new ExistsSubqueryPredicate(CriteriaUtils.requirePersistentEntitySubquery(subquery));
    }

    @Override

    public <Y> Expression<Y> all(Subquery<Y> subquery) {
        return new SubqueryExpression<>(SubqueryExpression.Type.ALL, CriteriaUtils.requirePersistentEntitySubquery(subquery));
    }

    @Override

    public <Y> Expression<Y> some(Subquery<Y> subquery) {
        return new SubqueryExpression<>(SubqueryExpression.Type.SOME, CriteriaUtils.requirePersistentEntitySubquery(subquery));
    }

    @Override

    public <Y> Expression<Y> any(Subquery<Y> subquery) {
        return new SubqueryExpression<>(SubqueryExpression.Type.ANY, CriteriaUtils.requirePersistentEntitySubquery(subquery));
    }

    @Override
    public <N, T extends java.time.temporal.Temporal> Expression<N> extract(TemporalField<N, T> field, Expression<T> temporal) {
        throw notSupportedOperation();
    }

    @Override

    public Predicate and(Expression<Boolean> x,  Expression<Boolean> y) {
        return new ConjunctionPredicate(List.of(requireBoolExpression(x), requireBoolExpression(y)));
    }

    @Override

    public Predicate and(Predicate... restrictions) {
        return and(List.of(restrictions));
    }

    @Override

    public Predicate and(Iterable<Predicate> restrictions) {
        return new ConjunctionPredicate(requireBoolExpressions(restrictions));
    }

    @Override
    public Predicate and(List<Predicate> restrictions) {
        return and((Iterable<Predicate>) restrictions);
    }

    @Override

    public Predicate isEmptyString(Expression<String> expression) {
        return new UnaryPredicate(expression, PredicateUnaryOp.IS_EMPTY);
    }

    @Override

    public Predicate isNotEmptyString(Expression<String> expression) {
        return new UnaryPredicate(expression, PredicateUnaryOp.IS_NOT_EMPTY);
    }

    @Override

    public Predicate ilike(Expression<String> x,  Expression<String> pattern) {
        return new LikePredicate(x, pattern, null, false, true);
    }

    @Override
    public Predicate endingWithString(Expression<String> x,  Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.ENDS_WITH);
    }

    @Override

    public Predicate startsWithString(Expression<String> x,  Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.STARTS_WITH);
    }

    @Override

    public Predicate containsString(Expression<String> x,  Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.CONTAINS);
    }

    @Override
    public Predicate containsStringIgnoreCase(Expression<String> x, Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.CONTAINS_IGNORE_CASE);
    }

    @Override

    public Predicate equalStringIgnoreCase(Expression<String> x,  String y) {
        return new BinaryPredicate(x, literal(y), PredicateBinaryOp.EQUALS_IGNORE_CASE);
    }

    @Override

    public Predicate equalStringIgnoreCase(Expression<String> x,  Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.EQUALS_IGNORE_CASE);
    }

    @Override

    public Predicate notEqualStringIgnoreCase(Expression<String> x,  String y) {
        return new BinaryPredicate(x, literal(y), PredicateBinaryOp.NOT_EQUALS_IGNORE_CASE);
    }

    @Override

    public Predicate notEqualStringIgnoreCase(Expression<String> x,  Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.NOT_EQUALS_IGNORE_CASE);
    }

    @Override

    public Predicate startsWithStringIgnoreCase(Expression<String> x,  Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.STARTS_WITH_IGNORE_CASE);
    }

    @Override

    public Predicate endingWithStringIgnoreCase(Expression<String> x,  Expression<String> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.ENDS_WITH_IGNORE_CASE);
    }

    @Override

    public Predicate or(Expression<Boolean> x,  Expression<Boolean> y) {
        return new DisjunctionPredicate(List.of(requireBoolExpression(x), requireBoolExpression(y)));
    }

    @Override

    public Predicate or(Predicate... restrictions) {
        return or(List.of(restrictions));
    }

    @Override

    public Predicate or(Iterable<Predicate> restrictions) {
        return new DisjunctionPredicate(requireBoolExpressions(restrictions));
    }

    @Override
    public Predicate or(List<Predicate> restrictions) {
        return or((Iterable<Predicate>) restrictions);
    }

    @Override

    public Predicate not(Expression<Boolean> restriction) {
        return new NegatedPredicate(requireBoolExpression(restriction));
    }

    @Override

    public Predicate conjunction() {
        return new ConjunctionPredicate(Collections.emptyList());
    }

    @Override

    public Predicate disjunction() {
        return new DisjunctionPredicate(Collections.emptyList());
    }

    @Override

    public Predicate isTrue(Expression<Boolean> x) {
        return new UnaryPredicate(x, PredicateUnaryOp.IS_TRUE);
    }

    @Override

    public Predicate isFalse(Expression<Boolean> x) {
        return new UnaryPredicate(x, PredicateUnaryOp.IS_FALSE);
    }

    @Override

    public Predicate isNull(Expression<?> x) {
        return new UnaryPredicate(x, PredicateUnaryOp.IS_NULL);
    }

    @Override

    public Predicate isNotNull(Expression<?> x) {
        return new UnaryPredicate(x, PredicateUnaryOp.IS_NON_NULL);
    }

    @Override

    public Predicate equal(Expression<?> x,  Expression<?> y) {
        return predicate(x, y, PredicateBinaryOp.EQUALS);
    }

    @Override
    @NextMajorVersion("Don't allow null values")
    public Predicate equal(Expression<?> x, @Nullable Object y) {
        return predicate(x, y, PredicateBinaryOp.EQUALS);
    }

    @Override
    public Predicate notEqual(Expression<?> x,  Expression<?> y) {
        return predicate(x, y, PredicateBinaryOp.NOT_EQUALS);
    }

    @Override
    public Predicate notEqual(Expression<?> x, @Nullable Object y) {
        return predicate(x, y, PredicateBinaryOp.NOT_EQUALS);
    }

    @Override
    public <Y extends Comparable<? super Y>> Predicate greaterThan(Expression<? extends Y> x,  Expression<? extends Y> y) {
        return comparable(x, y, PredicateBinaryOp.GREATER_THAN);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate greaterThan(Expression<? extends Y> x,  Y y) {
        return comparable(x, y, PredicateBinaryOp.GREATER_THAN);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate greaterThanOrEqualTo(Expression<? extends Y> x,  Expression<? extends Y> y) {
        return comparable(x, y, PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate greaterThanOrEqualTo(Expression<? extends Y> x,  Y y) {
        return comparable(x, y, PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate lessThan(Expression<? extends Y> x,  Expression<? extends Y> y) {
        return comparable(x, y, PredicateBinaryOp.LESS_THAN);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate lessThan(Expression<? extends Y> x,  Y y) {
        return comparable(x, y, PredicateBinaryOp.LESS_THAN);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate lessThanOrEqualTo(Expression<? extends Y> x,  Expression<? extends Y> y) {
        return comparable(x, y, PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate lessThanOrEqualTo(Expression<? extends Y> x, Y y) {
        return comparable(x, y, PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate between(Expression<? extends Y> v,  Expression<? extends Y> x,  Expression<? extends Y> y) {
        return new BetweenPredicate(v, x, y);
    }

    @Override

    public <Y extends Comparable<? super Y>> Predicate between(Expression<? extends Y> v,  Y x,  Y y) {
        return new BetweenPredicate(v, literal(Objects.requireNonNull(x)), literal(Objects.requireNonNull(y)));
    }

    @Override

    public Predicate gt(Expression<? extends Number> x,  Expression<? extends Number> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.GREATER_THAN);
    }

    @Override

    public Predicate gt(Expression<? extends Number> x,  Number y) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), PredicateBinaryOp.GREATER_THAN);
    }

    @Override

    public Predicate ge(Expression<? extends Number> x,  Expression<? extends Number> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override

    public Predicate ge(Expression<? extends Number> x,  Number y) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override

    public Predicate lt(Expression<? extends Number> x,  Expression<? extends Number> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.LESS_THAN);
    }

    @Override

    public Predicate lt(Expression<? extends Number> x,  Number y) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), PredicateBinaryOp.LESS_THAN);
    }

    @Override

    public Predicate le(Expression<? extends Number> x,  Expression<? extends Number> y) {
        return new BinaryPredicate(x, y, PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override

    public Predicate le(Expression<? extends Number> x,  Number y) {
        return new BinaryPredicate(x, literal(Objects.requireNonNull(y)), PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <N extends Number> Expression<N> neg(Expression<N> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <N extends Number> Expression<N> abs(Expression<N> x) {
        throw notSupportedOperation();
    }

    @Override

    public <N extends Number> Expression<N> sum(Expression<? extends N> x, Expression<? extends N> y) {
        return new BinaryExpression<>(x, y, BinaryExpressionType.SUM, (Class<N>) Number.class);
    }

    @Override

    public <N extends Number> Expression<N> sum(Expression<? extends N> x,  N y) {
        return new BinaryExpression<>(x, literal(y), BinaryExpressionType.SUM, (Class<N>) Number.class);
    }

    @Override

    public <N extends Number> Expression<N> sum(N x,  Expression<? extends N> y) {
        return new BinaryExpression<>(literal(x), y, BinaryExpressionType.SUM, (Class<N>) Number.class);
    }

    @Override

    public <N extends Number> Expression<N> prod(Expression<? extends N> x,  Expression<? extends N> y) {
        return new BinaryExpression<>(x, y, BinaryExpressionType.PROD, (Class<N>) Number.class);
    }

    @Override

    public <N extends Number> Expression<N> prod(Expression<? extends N> x,  N y) {
        return new BinaryExpression<>(x, literal(y), BinaryExpressionType.PROD, (Class<N>) Number.class);
    }

    @Override

    public <N extends Number> Expression<N> prod(N x,  Expression<? extends N> y) {
        return new BinaryExpression<>(literal(x), y, BinaryExpressionType.PROD, (Class<N>) Number.class);
    }

    @Override

    public <N extends Number> Expression<N> diff(Expression<? extends N> x,  Expression<? extends N> y) {
        return new BinaryExpression<>(x, y, BinaryExpressionType.DIFF, (Class<N>) Number.class);
    }

    @Override

    public <N extends Number> Expression<N> diff(Expression<? extends N> x,  N y) {
        return new BinaryExpression<>(x, literal(y), BinaryExpressionType.DIFF, (Class<N>) Number.class);
    }

    @Override

    public <N extends Number> Expression<N> diff(N x,  Expression<? extends N> y) {
        return new BinaryExpression<>(literal(y), y, BinaryExpressionType.DIFF, (Class<N>) Number.class);
    }

    @Override

    public Expression<Number> quot(Expression<? extends Number> x,  Expression<? extends Number> y) {
        return new BinaryExpression<>(x, y, BinaryExpressionType.QUOT, Number.class);
    }

    @Override

    public Expression<Number> quot(Expression<? extends Number> x,  Number y) {
        return new BinaryExpression<>(x, literal(y), BinaryExpressionType.QUOT, Number.class);
    }

    @Override

    public Expression<Number> quot(Number x,  Expression<? extends Number> y) {
        return new BinaryExpression<>(literal(x), y, BinaryExpressionType.QUOT, Number.class);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Integer> mod(Expression<Integer> x,  Expression<Integer> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    public Expression<Integer> mod(Expression<Integer> x,  Integer y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Integer> mod(Integer x,  Expression<Integer> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Double> sqrt(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Long> toLong(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Integer> toInteger(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Float> toFloat(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Double> toDouble(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<BigDecimal> toBigDecimal(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<BigInteger> toBigInteger(Expression<? extends Number> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> toString(Expression<Character> x) {
        throw notSupportedOperation();
    }

    @Override

    public <T> Expression<T> literal(@Nullable T value) {
        if (value instanceof Expression<?>) {
            throw new IllegalArgumentException("An expression cannot be literal");
        }
        return new LiteralExpression<>(value);
    }

    @Override

    public <T> Expression<T> nullLiteral(Class<T> x) {
        return new LiteralExpression<>(x);
    }

    @Override

    public <T> ParameterExpression<T> parameter(Class<T> paramClass) {
        return parameter(paramClass, null, null);
    }

    @Override
    public <T> ParameterExpression<T> parameter(Class<T> paramClass,  String name) {
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

    public <T> ParameterExpression<T> parameter(Class<T> paramClass, @Nullable String name, @Nullable Object value) {
        return new DefaultParameterExpression<>(paramClass, name, value);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <C extends Collection<?>> Predicate isEmpty(Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <C extends Collection<?>> Predicate isNotEmpty(Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <C extends Collection<?>> Expression<Integer> size(Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <C extends Collection<?>> Expression<Integer> size(C collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <E, C extends Collection<E>> Predicate isMember(Expression<E> elem,  Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <E, C extends Collection<E>> Predicate isMember(E elem,  Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <E, C extends Collection<E>> Predicate isNotMember(Expression<E> elem,  Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <E, C extends Collection<E>> Predicate isNotMember(E elem,  Expression<C> collection) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <V, M extends Map<?, V>> Expression<Collection<V>> values(M map) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <K, M extends Map<K, ?>> Expression<Set<K>> keys(M map) {
        throw notSupportedOperation();
    }

    @Override

    public Predicate regex(Expression<String> x,  Expression<String> pattern) {
        return new BinaryPredicate(x, pattern, PredicateBinaryOp.REGEX);
    }

    @Override

    public Predicate like(Expression<String> x,  Expression<String> pattern) {
        return new LikePredicate(x, pattern, null, false);
    }

    @Override

    public Predicate like(Expression<String> x,  String pattern) {
        return new LikePredicate(x, literal(pattern), null, false);
    }

    @Override

    public Predicate like(Expression<String> x,  Expression<String> pattern,  Expression<Character> escapeChar) {
        return new LikePredicate(x, pattern, escapeChar, false);
    }

    @Override

    public Predicate like(Expression<String> x,  Expression<String> pattern, char escapeChar) {
        return new LikePredicate(x, pattern, literal(escapeChar), false);
    }

    @Override

    public Predicate like(Expression<String> x,  String pattern,  Expression<Character> escapeChar) {
        return new LikePredicate(x, literal(pattern), escapeChar, false);
    }

    @Override

    public Predicate like(Expression<String> x,  String pattern, char escapeChar) {
        return new LikePredicate(x, literal(pattern), literal(escapeChar), false);
    }

    @Override

    public Predicate notLike(Expression<String> x,  Expression<String> pattern) {
        return new LikePredicate(x, pattern, null, true);
    }

    @Override

    public Predicate notLike(Expression<String> x,  String pattern) {
        return new LikePredicate(x, literal(pattern), null, true);
    }

    @Override

    public Predicate notLike(Expression<String> x,  Expression<String> pattern,  Expression<Character> escapeChar) {
        return new LikePredicate(x, pattern, escapeChar, true);
    }

    @Override

    public Predicate notLike(Expression<String> x,  Expression<String> pattern, char escapeChar) {
        return new LikePredicate(x, pattern, literal(escapeChar), true);
    }

    @Override

    public Predicate notLike(Expression<String> x,  String pattern,  Expression<Character> escapeChar) {
        return new LikePredicate(x, literal(pattern), escapeChar, true);
    }

    @Override

    public Predicate notLike(Expression<String> x,  String pattern, char escapeChar) {
        return new LikePredicate(x, literal(pattern), literal(escapeChar), true);
    }

    @Override

    public Expression<String> concat(Expression<String> x,  Expression<String> y) {
        return new BinaryExpression<>(x, y, BinaryExpressionType.CONCAT, String.class);
    }

    @Override

    public Expression<String> concat(Expression<String> x,  String y) {
        return new BinaryExpression<>(x, literal(y), BinaryExpressionType.CONCAT, String.class);
    }

    @Override

    public Expression<String> concat(String x,  Expression<String> y) {
        return new BinaryExpression<>(literal(x), y, BinaryExpressionType.CONCAT, String.class);
    }

    @Override
    public Expression<String> concat(List<Expression<String>> expressions) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<String> replace(Expression<String> x, Expression<String> substring, Expression<String> replacement) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<String> replace(Expression<String> x, Expression<String> substring, String replacement) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<String> replace(Expression<String> x, String substring, Expression<String> replacement) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<String> replace(Expression<String> x, String substring, String replacement) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> substring(Expression<String> x,  Expression<Integer> from) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> substring(Expression<String> x, int from) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> substring(Expression<String> x,  Expression<Integer> from,  Expression<Integer> len) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> substring(Expression<String> x, int from, int len) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<String> left(Expression<String> x, int len) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<String> left(Expression<String> x, Expression<Integer> len) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<String> right(Expression<String> x, int len) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<String> right(Expression<String> x, Expression<Integer> len) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> trim(Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> trim(Trimspec ts,  Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> trim(Expression<Character> t,  Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> trim(Trimspec ts,  Expression<Character> t,  Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> trim(char t,  Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<String> trim(Trimspec ts, char t,  Expression<String> x) {
        throw notSupportedOperation();
    }

    @Override

    public Expression<String> lower(Expression<String> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.LOWER);
    }

    @Override

    public Expression<String> upper(Expression<String> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.UPPER);
    }

    @Override

    public Expression<Integer> length(Expression<String> x) {
        return new UnaryExpression<>(x, UnaryExpressionType.LENGTH);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Integer> locate(Expression<String> x,  Expression<String> pattern) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Integer> locate(Expression<String> x,  String pattern) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Integer> locate(Expression<String> x,  Expression<String> pattern,  Expression<Integer> from) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public Expression<Integer> locate(Expression<String> x,  String pattern, int from) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Date> currentDate() {
        return new CurrentTemporalExpression<>(CurrentTemporalExpression.Type.DATE, Date.class);
    }

    @Override
    public Expression<Timestamp> currentTimestamp() {
        return new CurrentTemporalExpression<>(CurrentTemporalExpression.Type.TIMESTAMP, Timestamp.class);
    }

    @Override
    public Expression<Time> currentTime() {
        return new CurrentTemporalExpression<>(CurrentTemporalExpression.Type.TIME, Time.class);
    }

    @Override

    public <T> In<T> in(Expression<? extends T> expression) {
        return new InPredicate<>((Expression) expression, this);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <Y> Expression<Y> coalesce(Expression<? extends Y> x,  Expression<? extends Y> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <Y> Expression<Y> coalesce(Expression<? extends Y> x, Y y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <Y> Expression<Y> nullif(Expression<Y> x,  Expression<?> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <Y> Expression<Y> nullif(Expression<Y> x, Y y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <T> Coalesce<T> coalesce() {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <C, R> SimpleCase<C, R> selectCase(Expression<? extends C> expression) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <R> Case<R> selectCase() {
        throw notSupportedOperation();
    }

    @Override

    public <T> Expression<T> function(String name,  Class<T> type,  Expression<?>... args) {
        return new FunctionExpression<>(Objects.requireNonNull(name), List.of(args), Objects.requireNonNull(type));
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <X, T, V extends T> Join<X, V> treat(Join<X, T> join,  Class<V> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <X, T, E extends T> CollectionJoin<X, E> treat(CollectionJoin<X, T> join,  Class<E> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <X, T, E extends T> SetJoin<X, E> treat(SetJoin<X, T> join,  Class<E> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <X, T, E extends T> ListJoin<X, E> treat(ListJoin<X, T> join,  Class<E> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <X, K, T, V extends T> MapJoin<X, K, V> treat(MapJoin<X, K, T> join,  Class<V> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <X, T extends X> Path<T> treat(Path<X> path,  Class<T> type) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override

    public <X, T extends X> Root<T> treat(Root<X> root,  Class<T> type) {
        throw notSupportedOperation();
    }

    @Override
    public Predicate arrayContains(Expression<?> x, Expression<?> y) {
        return predicate(x, y, PredicateBinaryOp.ARRAY_CONTAINS);
    }

    @Override
    public Expression<LocalDate> localDate() {
        return parameter(LocalDate.class, null, (Supplier<LocalDate>) () -> localDateTimeSupplier.get().toLocalDate());
    }

    @Override
    public Expression<LocalDateTime> localDateTime() {
        return parameter(LocalDateTime.class, null, (Supplier<LocalDateTime>) () -> localDateTimeSupplier.get().toLocalDateTime());
    }

    @Override
    public Expression<LocalTime> localTime() {
        return parameter(LocalTime.class, null, (Supplier<LocalTime>) () -> localDateTimeSupplier.get().toLocalTime());
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
