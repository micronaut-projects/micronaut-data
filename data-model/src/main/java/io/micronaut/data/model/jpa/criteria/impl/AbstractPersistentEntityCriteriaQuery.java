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
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelPredicateVisitor;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelSelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.jpa.criteria.impl.util.Joiner;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.EntityType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.hasVersionPredicate;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;

/**
 * The abstract implementation of {@link PersistentEntityCriteriaQuery}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPersistentEntityCriteriaQuery<T> implements PersistentEntityCriteriaQuery<T>,
        QueryResultPersistentEntityCriteriaQuery {

    protected final Class<T> resultType;
    protected Predicate predicate;
    protected Selection<?> selection;
    protected PersistentEntityRoot<?> entityRoot;
    protected List<Order> orders;
    protected int max = -1;
    protected int offset = 0;
    protected boolean forUpdate;
    protected boolean distinct;

    protected AbstractPersistentEntityCriteriaQuery(Class<T> resultType) {
        this.resultType = resultType;
    }

    @Override
    public QueryResult buildQuery(QueryBuilder queryBuilder) {
        return queryBuilder.buildQuery(getQueryModel());
    }

    @NonNull
    @Override
    public QueryModel getQueryModel() {
        if (entityRoot == null) {
            throw new IllegalStateException("The root entity must be specified!");
        }
        QueryModel qm = QueryModel.from(entityRoot.getPersistentEntity());
        Joiner joiner = new Joiner();
        if (predicate instanceof PredicateVisitable) {
            PredicateVisitable predicate = (PredicateVisitable) this.predicate;
            predicate.accept(createPredicateVisitor(qm));
            predicate.accept(joiner);
        }
        if (selection instanceof SelectionVisitable) {
            SelectionVisitable selection = (SelectionVisitable) this.selection;
            selection.accept(new QueryModelSelectionVisitor(qm, distinct));
            selection.accept(joiner);
            SelectionVisitable entityRoot = (SelectionVisitable) this.entityRoot;
            entityRoot.accept(joiner);
        } else {
            SelectionVisitable entityRoot = (SelectionVisitable) this.entityRoot;
            entityRoot.accept(new QueryModelSelectionVisitor(qm, distinct));
            entityRoot.accept(joiner);
        }
        if (orders != null && !orders.isEmpty()) {
            List<Sort.Order> sortOrders = orders.stream().map(o -> {
                PersistentPropertyPath<?> propertyPath = requireProperty(o.getExpression());
                joiner.joinIfNeeded(propertyPath);
                String name = propertyPath.getPathAsString();
                if (o.isAscending()) {
                    return Sort.Order.asc(name);
                }
                return Sort.Order.desc(name);
            }).collect(Collectors.toList());
            qm.sort(Sort.of(sortOrders));
        }
        for (Map.Entry<String, Joiner.Joined> e : joiner.getJoins().entrySet()) {
            qm.join(e.getKey(), Optional.ofNullable(e.getValue().getType()).orElse(Join.Type.DEFAULT), e.getValue().getAlias());
        }

        qm.max(max);
        qm.offset(offset);
        if (forUpdate) {
            qm.forUpdate();
        }
        return qm;
    }

    /**
     * Creates query model predicate visitor.
     * @param queryModel The query model
     * @return the visitor
     */
    @NonNull
    protected QueryModelPredicateVisitor createPredicateVisitor(QueryModel queryModel) {
        return new QueryModelPredicateVisitor(queryModel);
    }

    @Override
    public PersistentEntityCriteriaQuery<T> max(int max) {
        this.max = max;
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> offset(int offset) {
        this.offset = offset;
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> forUpdate(boolean forUpdate) {
        this.forUpdate = forUpdate;
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
            this.selection = selections.length == 1 ? selections[0] : new CompoundSelection<>(Arrays.asList(selections));
        } else {
            this.selection = null;
        }
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> multiselect(List<Selection<?>> selectionList) {
        Objects.requireNonNull(selectionList);
        if (!selectionList.isEmpty()) {
            this.selection = selectionList.size() == 1 ? selectionList.iterator().next() : new CompoundSelection<>(selectionList);
        } else {
            this.selection = null;
        }
        return this;
    }

    @Override
    public abstract <X> PersistentEntityRoot<X> from(Class<X> entityClass);

    @Override
    public abstract <X> PersistentEntityRoot<X> from(PersistentEntity persistentEntity);

    @Override
    public <X> PersistentEntityRoot<X> from(EntityType<X> entity) {
        if (entityRoot != null) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        return null;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> where(Expression<Boolean> restriction) {
        predicate = new ConjunctionPredicate(Collections.singleton((IExpression<Boolean>) restriction));
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> where(Predicate... restrictions) {
        Objects.requireNonNull(restrictions);
        if (restrictions.length > 0) {
            predicate = restrictions.length == 1 ? restrictions[0] : new ConjunctionPredicate(
                    Arrays.stream(restrictions).sequential().map(x -> (IExpression<Boolean>) x).collect(Collectors.toList())
            );
        } else {
            predicate = null;
        }
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> groupBy(Expression<?>... grouping) {
        throw notSupportedOperation();
    }

    @Override
    public PersistentEntityCriteriaQuery<T> groupBy(List<Expression<?>> grouping) {
        throw notSupportedOperation();
    }

    @Override
    public PersistentEntityCriteriaQuery<T> having(Expression<Boolean> restriction) {
        throw notSupportedOperation();
    }

    @Override
    public PersistentEntityCriteriaQuery<T> having(Predicate... restrictions) {
        throw notSupportedOperation();
    }

    @Override
    public PersistentEntityCriteriaQuery<T> orderBy(Order... o) {
        orders = Arrays.asList(Objects.requireNonNull(o));
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> orderBy(List<Order> o) {
        orders = Objects.requireNonNull(o);
        return this;
    }

    @Override
    public PersistentEntityCriteriaQuery<T> distinct(boolean distinct) {
        this.distinct = distinct;
        return this;
    }

    @Override
    public Set<Root<?>> getRoots() {
        if (entityRoot != null) {
            return Collections.singleton(entityRoot);
        }
        return Collections.emptySet();
    }

    @Override
    public List<Expression<?>> getGroupList() {
        throw notSupportedOperation();
    }

    @Override
    public Predicate getGroupRestriction() {
        throw notSupportedOperation();
    }

    @Override
    public boolean isDistinct() {
        return false;
    }

    @Override
    public Class<T> getResultType() {
        return resultType;
    }

    @Override
    public List<Order> getOrderList() {
        throw notSupportedOperation();
    }

    @Override
    public Set<ParameterExpression<?>> getParameters() {
        throw notSupportedOperation();
    }

    @Override
    public <U> Subquery<U> subquery(Class<U> type) {
        throw notSupportedOperation();
    }

    @Override
    public Selection<T> getSelection() {
        return (Selection<T>) selection;
    }

    @Override
    public Predicate getRestriction() {
        return predicate;
    }

    public final boolean hasOnlyIdRestriction() {
        return isOnlyIdRestriction(predicate);
    }

    private boolean isOnlyIdRestriction(Expression<?> predicate) {
        if (predicate instanceof PersistentPropertyBinaryPredicate<?> pp) {
            return pp.getProperty() == pp.getProperty().getOwner().getIdentity();
        }
        if (predicate instanceof ConjunctionPredicate conjunctionPredicate) {
            if (conjunctionPredicate.getPredicates().size() == 1) {
                return isOnlyIdRestriction(conjunctionPredicate.getPredicates().iterator().next());
            }
        }
        if (predicate instanceof DisjunctionPredicate disjunctionPredicate) {
            if (disjunctionPredicate.getPredicates().size() == 1) {
                return isOnlyIdRestriction(disjunctionPredicate.getPredicates().iterator().next());
            }
        }
        return false;
    }

    public final boolean hasVersionRestriction() {
        if (entityRoot.getPersistentEntity().getVersion() == null) {
            return false;
        }
        return hasVersionPredicate(predicate);
    }

}
