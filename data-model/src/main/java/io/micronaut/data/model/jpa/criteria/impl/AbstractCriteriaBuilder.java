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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyUnaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateBinaryOp;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateUnaryOp;
import io.micronaut.data.model.jpa.criteria.impl.selection.AggregateExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.AggregateType;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireBoolExpression;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireBoolExpressions;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireBoolProperty;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireNumericProperty;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireNumericPropertyParameterOrLiteral;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requirePropertyOrRoot;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requirePropertyParameterOrLiteral;

/**
 * Abstract {@link jakarta.persistence.criteria.CriteriaBuilder} implementation.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractCriteriaBuilder implements PersistentEntityCriteriaBuilder {

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
        return new AggregateExpression<>(requireNumericProperty(x), AggregateType.AVG);
    }

    @Override
    @NonNull
    public <N extends Number> Expression<N> sum(@NonNull Expression<N> x) {
        return new AggregateExpression<>(requireNumericProperty(x), AggregateType.SUM);
    }

    @Override
    @NonNull
    public Expression<Long> sumAsLong(@NonNull Expression<Integer> x) {
        return new AggregateExpression<>(requireNumericProperty(x), AggregateType.SUM, Long.class);
    }

    @Override
    @NonNull
    public Expression<Double> sumAsDouble(@NonNull Expression<Float> x) {
        return new AggregateExpression<>(requireNumericProperty(x), AggregateType.SUM, Double.class);
    }

    @Override
    @NonNull
    public <N extends Number> Expression<N> max(@NonNull Expression<N> x) {
        return new AggregateExpression<>(requireNumericProperty(x), AggregateType.MAX);
    }

    @Override
    @NonNull
    public <N extends Number> Expression<N> min(@NonNull Expression<N> x) {
        return new AggregateExpression<>(requireNumericProperty(x), AggregateType.MIN);
    }

    @Override
    @NonNull
    public <X extends Comparable<? super X>> Expression<X> greatest(@NonNull Expression<X> x) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public <X extends Comparable<? super X>> Expression<X> least(@NonNull Expression<X> x) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public Expression<Long> count(@NonNull Expression<?> x) {
        return new AggregateExpression<>(requirePropertyOrRoot(x), AggregateType.COUNT, Long.class);
    }

    @Override
    @NonNull
    public Expression<Long> countDistinct(@NonNull Expression<?> x) {
        return new AggregateExpression<>(requirePropertyOrRoot(x), AggregateType.COUNT_DISTINCT, Long.class);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate exists(@NonNull Subquery<?> subquery) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <Y> Expression<Y> all(@NonNull Subquery<Y> subquery) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <Y> Expression<Y> some(@NonNull Subquery<Y> subquery) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <Y> Expression<Y> any(@NonNull Subquery<Y> subquery) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public Predicate and(@NonNull Expression<Boolean> x, @NonNull Expression<Boolean> y) {
        return new ConjunctionPredicate(Arrays.asList(requireBoolExpression(x), requireBoolExpression(y)));
    }

    @Override
    @NonNull
    public Predicate and(@NonNull Predicate... restrictions) {
        return and(Arrays.asList(restrictions));
    }

    @Override
    @NonNull
    public Predicate and(@NonNull Iterable<Predicate> restrictions) {
        return new ConjunctionPredicate(requireBoolExpressions(restrictions));
    }

    @Override
    @NonNull
    public Predicate isEmptyString(@NonNull Expression<String> expression) {
        return new PersistentPropertyUnaryPredicate<>(requireProperty(expression), PredicateUnaryOp.IS_EMPTY);
    }

    @Override
    @NonNull
    public Predicate isNotEmptyString(@NonNull Expression<String> expression) {
        return new PersistentPropertyUnaryPredicate<>(requireProperty(expression), PredicateUnaryOp.IS_NOT_EMPTY);
    }

    @Override
    @NonNull
    public Predicate rlikeString(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.RLIKE);
    }

    @Override
    @NonNull
    public Predicate ilikeString(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.ILIKE);
    }

    @Override
    public Predicate endingWithString(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.ENDS_WITH);
    }

    @Override
    @NonNull
    public Predicate startsWithString(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.STARTS_WITH);
    }

    @Override
    @NonNull
    public Predicate containsString(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.CONTAINS);
    }

    @Override
    @NonNull
    public Predicate equalStringIgnoreCase(@NonNull Expression<String> x, @NonNull String y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), literal(y), PredicateBinaryOp.EQUALS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate equalStringIgnoreCase(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.EQUALS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate notEqualStringIgnoreCase(@NonNull Expression<String> x, @NonNull String y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), literal(y), PredicateBinaryOp.NOT_EQUALS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate notEqualStringIgnoreCase(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.NOT_EQUALS_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate startsWithStringIgnoreCase(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.STARTS_WITH_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate endingWithStringIgnoreCase(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.ENDS_WITH_IGNORE_CASE);
    }

    @Override
    @NonNull
    public Predicate or(@NonNull Expression<Boolean> x, @NonNull Expression<Boolean> y) {
        return new DisjunctionPredicate(Arrays.asList(requireBoolExpression(x), requireBoolExpression(y)));
    }

    @Override
    @NonNull
    public Predicate or(@NonNull Predicate... restrictions) {
        return or(Arrays.asList(restrictions));
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
        return new PersistentPropertyUnaryPredicate<>(requireBoolProperty(x), PredicateUnaryOp.IS_TRUE);
    }

    @Override
    @NonNull
    public Predicate isFalse(@NonNull Expression<Boolean> x) {
        return new PersistentPropertyUnaryPredicate<>(requireProperty(x), PredicateUnaryOp.IS_FALSE);
    }

    @Override
    @NonNull
    public Predicate isNull(@NonNull Expression<?> x) {
        return new PersistentPropertyUnaryPredicate<>(requireProperty(x), PredicateUnaryOp.IS_NULL);
    }

    @Override
    @NonNull
    public Predicate isNotNull(@NonNull Expression<?> x) {
        return new PersistentPropertyUnaryPredicate<>(requireProperty(x), PredicateUnaryOp.IS_NON_NULL);
    }

    @Override
    @NonNull
    public Predicate equal(@NonNull Expression<?> x, @NonNull Expression<?> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.EQUALS);
    }

    @Override
    @NonNull
    public Predicate equal(@NonNull Expression<?> x, @Nullable Object y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.EQUALS);
    }

    @Override
    @NonNull
    public Predicate notEqual(@NonNull Expression<?> x, @NonNull Expression<?> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.NOT_EQUALS);
    }

    @Override
    @NonNull
    public Predicate notEqual(@NonNull Expression<?> x, @Nullable Object y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.NOT_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate greaterThan(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.GREATER_THAN);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate greaterThan(@NonNull Expression<? extends Y> x, Y y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.GREATER_THAN);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate greaterThanOrEqualTo(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate greaterThanOrEqualTo(@NonNull Expression<? extends Y> x, Y y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate lessThan(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.LESS_THAN);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate lessThan(@NonNull Expression<? extends Y> x, @NonNull Y y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.LESS_THAN);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate lessThanOrEqualTo(@NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(y), PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate lessThanOrEqualTo(@NonNull Expression<? extends Y> x, Y y) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate between(@NonNull Expression<? extends Y> v, @NonNull Expression<? extends Y> x, @NonNull Expression<? extends Y> y) {
        return new PersistentPropertyBetweenPredicate<>(requireProperty(v), requireNumericPropertyParameterOrLiteral(x), requireNumericPropertyParameterOrLiteral(y));
    }

    @Override
    @NonNull
    public <Y extends Comparable<? super Y>> Predicate between(@NonNull Expression<? extends Y> v, @NonNull Y x, @NonNull Y y) {
        return new PersistentPropertyBetweenPredicate<>(requireProperty(v), Objects.requireNonNull(x), Objects.requireNonNull(y));
    }

    @Override
    @NonNull
    public Predicate gt(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        return new PersistentPropertyBinaryPredicate<>(requireNumericProperty(x), requireNumericPropertyParameterOrLiteral(y), PredicateBinaryOp.GREATER_THAN);
    }

    @Override
    @NonNull
    public Predicate gt(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        return new PersistentPropertyBinaryPredicate<>(requireNumericProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.GREATER_THAN);
    }

    @Override
    @NonNull
    public Predicate ge(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        return new PersistentPropertyBinaryPredicate<>(requireNumericProperty(x), requireNumericPropertyParameterOrLiteral(y), PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public Predicate ge(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        return new PersistentPropertyBinaryPredicate<>(requireNumericProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.GREATER_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public Predicate lt(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        return new PersistentPropertyBinaryPredicate<>(requireNumericProperty(x), requireNumericPropertyParameterOrLiteral(y), PredicateBinaryOp.LESS_THAN);
    }

    @Override
    @NonNull
    public Predicate lt(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        return new PersistentPropertyBinaryPredicate<>(requireNumericProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.LESS_THAN);
    }

    @Override
    @NonNull
    public Predicate le(@NonNull Expression<? extends Number> x, @NonNull Expression<? extends Number> y) {
        return new PersistentPropertyBinaryPredicate<>(requireNumericProperty(x), requireNumericPropertyParameterOrLiteral(y), PredicateBinaryOp.LESS_THAN_OR_EQUALS);
    }

    @Override
    @NonNull
    public Predicate le(@NonNull Expression<? extends Number> x, @NonNull Number y) {
        return new PersistentPropertyBinaryPredicate<>(requireNumericProperty(x), Objects.requireNonNull(literal(y)), PredicateBinaryOp.LESS_THAN_OR_EQUALS);
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

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> sum(@NonNull Expression<? extends N> x, Expression<? extends N> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> sum(@NonNull Expression<? extends N> x, @NonNull N y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <N extends Number> Expression<N> sum(@NonNull N x, @NonNull Expression<? extends N> y) {
        throw notSupportedOperation();
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
    public <T> Expression<T> literal(@NonNull T value) {
        return new LiteralExpression<>(value);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <T> Expression<T> nullLiteral(@NonNull Class<T> x) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    public <T> ParameterExpression<T> parameter(@NonNull Class<T> paramClass) {
        return new ParameterExpressionImpl<T>(paramClass, null) {

            @Override
            public QueryParameterBinding bind(BindingContext bindingContext) {
                String name = bindingContext.getName() == null ? String.valueOf(bindingContext.getIndex()) : bindingContext.getName();
                PersistentPropertyPath outgoingQueryParameterProperty = bindingContext.getOutgoingQueryParameterProperty();
                return new QueryParameterBinding() {
                    @Override
                    public String getKey() {
                        return name;
                    }

                    @Override
                    public DataType getDataType() {
                        return outgoingQueryParameterProperty.getProperty().getDataType();
                    }

                    @Override
                    public String[] getPropertyPath() {
                        return asStringPath(outgoingQueryParameterProperty.getAssociations(), outgoingQueryParameterProperty.getProperty());
                    }

                    @Override
                    public boolean isExpandable() {
                        return bindingContext.isExpandable();
                    }
                };
            }
        };
    }

    private String[] asStringPath(List<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return new String[]{property.getName()};
        }
        List<String> path = new ArrayList<>(associations.size() + 1);
        for (Association association : associations) {
            path.add(association.getName());
        }
        path.add(property.getName());
        return path.toArray(new String[0]);
    }

    @Override
    @NonNull
    public <T> ParameterExpression<T> parameter(@NonNull Class<T> paramClass, @NonNull String name) {
        return new ParameterExpressionImpl<T>(paramClass, name) {

            @Override
            public QueryParameterBinding bind(BindingContext bindingContext) {
                String name = bindingContext.getName() == null ? String.valueOf(bindingContext.getIndex()) : bindingContext.getName();
                PersistentPropertyPath outgoingQueryParameterProperty = bindingContext.getOutgoingQueryParameterProperty();
                return new QueryParameterBinding() {
                    @Override
                    public String getKey() {
                        return name;
                    }

                    @Override
                    public DataType getDataType() {
                        return outgoingQueryParameterProperty.getProperty().getDataType();
                    }

                    @Override
                    public String[] getPropertyPath() {
                        return asStringPath(outgoingQueryParameterProperty.getAssociations(), outgoingQueryParameterProperty.getProperty());
                    }
                };
            }
        };
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
    public Predicate like(@NonNull Expression<String> x, @NonNull Expression<String> pattern) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(pattern), PredicateBinaryOp.LIKE);
    }

    @Override
    @NonNull
    public Predicate regex(@NonNull Expression<String> x, @NonNull Expression<String> pattern) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), requirePropertyParameterOrLiteral(pattern), PredicateBinaryOp.REGEX);
    }

    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull String pattern) {
        return new PersistentPropertyBinaryPredicate<>(requireProperty(x), literal(pattern), PredicateBinaryOp.LIKE);
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull Expression<String> pattern, @NonNull Expression<Character> escapeChar) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull Expression<String> pattern, char escapeChar) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull String pattern, @NonNull Expression<Character> escapeChar) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate like(@NonNull Expression<String> x, @NonNull String pattern, char escapeChar) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull Expression<String> pattern) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull String pattern) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull Expression<String> pattern, @NonNull Expression<Character> escapeChar) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull Expression<String> pattern, char escapeChar) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull String pattern, @NonNull Expression<Character> escapeChar) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Predicate notLike(@NonNull Expression<String> x, @NonNull String pattern, char escapeChar) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> concat(@NonNull Expression<String> x, @NonNull Expression<String> y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> concat(@NonNull Expression<String> x, @NonNull String y) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> concat(@NonNull String x, @NonNull Expression<String> y) {
        throw notSupportedOperation();
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

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> lower(@NonNull Expression<String> x) {
        throw notSupportedOperation();
    }

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Expression<String> upper(@NonNull Expression<String> x) {
        throw notSupportedOperation();
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

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <T> In<T> in(Expression<? extends T> expression) {
        throw notSupportedOperation();
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

    /**
     * Not supported yet.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public <T> Expression<T> function(@NonNull String name, @NonNull Class<T> type, @NonNull Expression<?>... args) {
        throw notSupportedOperation();
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

}
