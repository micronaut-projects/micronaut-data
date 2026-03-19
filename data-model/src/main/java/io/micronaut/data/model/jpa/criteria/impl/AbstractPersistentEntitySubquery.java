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
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.expression.ClassExpressionType;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CollectionJoin;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The abstract implementation of {@link PersistentEntityCriteriaQuery}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public abstract class AbstractPersistentEntitySubquery<T> extends AbstractPersistentEntityQuery<T, PersistentEntitySubquery<T>>
    implements PersistentEntitySubquery<T>, IExpression<T> {

    private final AbstractQuery<?> parentQuery;
    private final Set<Join<?, ?>> correlatedJoins = new LinkedHashSet<>();

    protected AbstractPersistentEntitySubquery(AbstractQuery<?> parentQuery, ExpressionType<T> resultType, CriteriaBuilder criteriaBuilder) {
        super(resultType, criteriaBuilder);
        this.parentQuery = parentQuery;
    }

    @Override
    public PersistentEntitySubquery<T> orderBy(Order... orders) {
        this.orders = Arrays.asList(Objects.requireNonNull(orders));
        return this;
    }

    @Override
    public PersistentEntitySubquery<T> orderBy(List<Order> orders) {
        this.orders = Objects.requireNonNull(orders);
        return this;
    }

    @Override
    public AbstractQuery<?> getParent() {
        return parentQuery;
    }

    @Override
    protected PersistentEntitySubquery<T> self() {
        return this;
    }

    @Override
    @Nullable
    public IExpression<T> getSelection() {
        return (IExpression<T>) super.getSelection();
    }

    @Override
    public ExpressionType<T> getExpressionType() {
        return Objects.requireNonNull(getSelection()).getExpressionType();
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public PersistentEntitySubquery<T> select(Expression<T> expression) {
        this.selection = expression;
        return this;
    }

    @Override
    public Set<Join<?, ?>> getCorrelatedJoins() {
        return Set.copyOf(correlatedJoins);
    }

    @Override
    public CommonAbstractCriteria getContainingQuery() {
        return parentQuery;
    }

    @Override
    public Set<jakarta.persistence.criteria.ParameterExpression<?>> getParameters() {
        return CriteriaUtils.extractParameters(predicate, selection, orders);
    }

    @Override
    public <U> Subquery<U> subquery(EntityType<U> type) {
        return subquery(new ClassExpressionType<>(Objects.requireNonNull(type).getJavaType()));
    }

    @Override
    public <X, K, V> MapJoin<X, K, V> correlate(MapJoin<X, K, V> parentMap) {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public <X, Y> ListJoin<X, Y> correlate(ListJoin<X, Y> parentList) {
        return (ListJoin<X, Y>) correlate((Join<X, Y>) parentList);
    }

    @Override
    public <X, Y> SetJoin<X, Y> correlate(SetJoin<X, Y> parentSet) {
        return (SetJoin<X, Y>) correlate((Join<X, Y>) parentSet);
    }

    @Override
    public <X, Y> CollectionJoin<X, Y> correlate(CollectionJoin<X, Y> parentCollection) {
        return (CollectionJoin<X, Y>) correlate((Join<X, Y>) parentCollection);
    }

    @Override
    public <X, Y> Join<X, Y> correlate(Join<X, Y> parentJoin) {
        Join<X, Y> correlatedJoin = Objects.requireNonNull(parentJoin);
        correlatedJoins.add(correlatedJoin);
        return correlatedJoin;
    }

    @Override
    public <Y> Root<Y> correlate(Root<Y> parentRoot) {
        Root<Y> correlatedRoot = Objects.requireNonNull(parentRoot);
        entityRoot = (io.micronaut.data.model.jpa.criteria.PersistentEntityRoot<?>) correlatedRoot;
        return correlatedRoot;
    }

}
