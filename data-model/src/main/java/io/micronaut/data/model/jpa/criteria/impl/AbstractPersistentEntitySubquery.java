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
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CollectionJoin;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;

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

    protected AbstractPersistentEntitySubquery(AbstractQuery<?> parentQuery, Class<T> resultType, CriteriaBuilder criteriaBuilder) {
        super(resultType, criteriaBuilder);
        this.parentQuery = parentQuery;
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
    public IExpression<T> getSelection() {
        return (IExpression<T>) super.getSelection();
    }

    @Override
    public ExpressionType<T> getExpressionType() {
        return getSelection().getExpressionType();
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
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public CommonAbstractCriteria getContainingQuery() {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public <X, K, V> MapJoin<X, K, V> correlate(MapJoin<X, K, V> parentMap) {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public <X, Y> ListJoin<X, Y> correlate(ListJoin<X, Y> parentList) {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public <X, Y> SetJoin<X, Y> correlate(SetJoin<X, Y> parentSet) {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public <X, Y> CollectionJoin<X, Y> correlate(CollectionJoin<X, Y> parentCollection) {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public <X, Y> Join<X, Y> correlate(Join<X, Y> parentJoin) {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public <Y> Root<Y> correlate(Root<Y> parentRoot) {
        throw CriteriaUtils.notSupportedOperation();
    }

}
