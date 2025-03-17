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
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Selection;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The abstract implementation of {@link PersistentEntityCriteriaQuery}.
 *
 * @param <T> The result type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPersistentEntityCriteriaQuery<T> extends AbstractPersistentEntityQuery<T, PersistentEntityCriteriaQuery<T>> implements PersistentEntityCriteriaQuery<T> {

    protected AbstractPersistentEntityCriteriaQuery(ExpressionType<T> resultType, CriteriaBuilder criteriaBuilder) {
        super(resultType, criteriaBuilder);
    }

    @Override
    protected PersistentEntityCriteriaQuery<T> self() {
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> select(Selection<? extends T> selection) {
        this.selection = Objects.requireNonNull(selection);
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> multiselect(Selection<?>... selections) {
        Objects.requireNonNull(selections);
        if (selections.length > 0) {
            this.selection = new CompoundSelection<>(Arrays.asList(selections));
        } else {
            this.selection = null;
        }
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> multiselect(List<Selection<?>> selectionList) {
        Objects.requireNonNull(selectionList);
        if (!selectionList.isEmpty()) {
            this.selection = new CompoundSelection<>(selectionList);
        } else {
            this.selection = null;
        }
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> orderBy(Order... orders) {
        this.orders = Arrays.asList(Objects.requireNonNull(orders));
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> orderBy(List<Order> orders) {
        this.orders = Objects.requireNonNull(orders);
        return this;
    }

    @Override
    public List<Order> getOrderList() {
        throw notSupportedOperation();
    }

    @Override
    public Set<ParameterExpression<?>> getParameters() {
        throw notSupportedOperation();
    }

}
