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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.ISelection;
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
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.EntityType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

    protected final CriteriaBuilder criteriaBuilder;
    protected final Class<T> resultType;
    protected Predicate predicate;
    protected Selection<?> selection;
    protected PersistentEntityRoot<?> entityRoot;
    protected List<Order> orders;
    protected int max = -1;
    protected int offset = 0;
    protected boolean forUpdate;
    protected boolean distinct;

    protected AbstractPersistentEntityCriteriaQuery(Class<T> resultType, CriteriaBuilder criteriaBuilder) {
        this.resultType = resultType;
        this.criteriaBuilder = criteriaBuilder;
    }

    @Override
    public QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryBuilder2 queryBuilder) {
        SelectQueryDefinitionImpl definition = new SelectQueryDefinitionImpl(
            entityRoot.getPersistentEntity(),
            predicate,
            selection == null ? entityRoot : selection,
            calculateJoins(entityRoot.getPersistentEntity(), false),
            forUpdate,
            distinct,
            orders == null ? List.of() : orders,
            max,
            offset
        );
        return queryBuilder.buildSelect(annotationMetadata, definition);
    }

    @Override
    public QueryResult buildCountQuery(AnnotationMetadata annotationMetadata, QueryBuilder2 queryBuilder) {
        SelectQueryDefinitionImpl definition = new SelectQueryDefinitionImpl(
            entityRoot.getPersistentEntity(),
            predicate,
            criteriaBuilder.count(entityRoot),
            calculateJoins(entityRoot.getPersistentEntity(), true),
            false,
            distinct,
            List.of(),
            -1,
            -1
        );
        return queryBuilder.buildSelect(annotationMetadata, definition);
    }

    private Map<String, JoinPath> calculateJoins(PersistentEntity persistentEntity, boolean remapFetchJoins) {
        Joiner joiner = new Joiner();
        if (predicate instanceof IPredicate predicateVisitable) {
            predicateVisitable.visitPredicate(joiner);
        }
        if (selection instanceof ISelection<?> selectionVisitable) {
            selectionVisitable.visitSelection(joiner);
            entityRoot.visitSelection(joiner);
        } else {
            entityRoot.visitSelection(joiner);
        }
        if (orders != null) {
            for (Order o : orders) {
                joiner.joinIfNeeded(requireProperty(o.getExpression()));
            }
        }
        Map<String, JoinPath> joinPaths = new LinkedHashMap<>();
        for (Map.Entry<String, Joiner.Joined> e : joiner.getJoins().entrySet()) {
            Join.Type joinType = Optional.ofNullable(e.getValue().getType()).orElse(Join.Type.DEFAULT);
            if (remapFetchJoins) {
                Association association = e.getValue().getAssociation().getAssociation();
                if (association != null && !association.getKind().isSingleEnded()) {
                    // skip OneToMany and ManyToMany
                    continue;
                }
                joinType = switch (joinType) {
                    case INNER, FETCH -> Join.Type.DEFAULT;
                    case LEFT_FETCH -> Join.Type.LEFT;
                    case RIGHT_FETCH -> Join.Type.RIGHT;
                    default -> joinType;
                };
            }
            String path = e.getKey();
            io.micronaut.data.model.PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(path);
            if (propertyPath == null) {
                throw new IllegalArgumentException("Invalid association path. Element [" + path + "] is not an association for [" + persistentEntity + "]");
            }
            Association[] associationPath;
            if (propertyPath.getProperty() instanceof Association) {
                associationPath = Stream.concat(
                    propertyPath.getAssociations().stream(),
                    Stream.of(propertyPath.getProperty())
                ).toArray(Association[]::new);
            } else {
                associationPath = propertyPath.getAssociations().toArray(new Association[0]);
            }
            JoinPath jp = new JoinPath(path, associationPath, joinType, e.getValue().getAlias());
            joinPaths.put(e.getKey(), jp);
        }
        return joinPaths;
    }

    @NonNull
    @Override
    public QueryModel getQueryModel() {
        if (entityRoot == null) {
            throw new IllegalStateException("The root entity must be specified!");
        }
        QueryModel qm = QueryModel.from(entityRoot.getPersistentEntity());
        Joiner joiner = new Joiner();
        if (predicate instanceof IPredicate predicateVisitable) {
            predicateVisitable.visitPredicate(createPredicateVisitor(qm));
            predicateVisitable.visitPredicate(joiner);
        }
        if (selection instanceof ISelection<?> selectionVisitable) {
            selectionVisitable.visitSelection(new QueryModelSelectionVisitor(qm, distinct));
            selectionVisitable.visitSelection(joiner);
            entityRoot.visitSelection(joiner);
        } else {
            entityRoot.visitSelection(new QueryModelSelectionVisitor(qm, distinct));
            entityRoot.visitSelection(joiner);
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
            }).toList();
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
     *
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
                Arrays.stream(restrictions).sequential().map(x -> (IExpression<Boolean>) x).toList()
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
        return distinct;
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

    @Internal
    private static final class SelectQueryDefinitionImpl extends BaseQueryDefinitionImpl implements QueryBuilder2.SelectQueryDefinition {

        private final Selection<?> selection;
        private final boolean isForUpdate;
        private final boolean isDistinct;
        private final List<Order> order;
        private final int limit;
        private final int offset;

        public SelectQueryDefinitionImpl(PersistentEntity persistentEntity,
                                         Predicate predicate,
                                         Selection<?> selection,
                                         Map<String, JoinPath> joinPaths,
                                         boolean isForUpdate,
                                         boolean isDistinct,
                                         List<Order> order,
                                         int limit,
                                         int offset) {
            super(persistentEntity, predicate, joinPaths);
            this.selection = selection;
            this.isForUpdate = isForUpdate;
            this.isDistinct = isDistinct;
            this.order = order;
            this.limit = limit;
            this.offset = offset;
        }

        @Override
        public Selection<?> selection() {
            return selection;
        }

        @Override
        public List<Order> order() {
            return order;
        }

        @Override
        public int limit() {
            return limit;
        }

        @Override
        public int offset() {
            return offset;
        }

        @Override
        public boolean isForUpdate() {
            return isForUpdate;
        }

        @Override
        public boolean isDistinct() {
            return isDistinct;
        }
    }

    @Internal
    abstract static class BaseQueryDefinitionImpl implements QueryBuilder2.BaseQueryDefinition {

        private final PersistentEntity persistentEntity;
        private final Predicate predicate;
        private final Map<String, JoinPath> joinPaths;

        protected BaseQueryDefinitionImpl(PersistentEntity persistentEntity,
                                          Predicate predicate,
                                          Map<String, JoinPath> joinPaths) {
            this.persistentEntity = persistentEntity;
            this.predicate = predicate;
            this.joinPaths = joinPaths;
        }

        @Override
        public PersistentEntity persistentEntity() {
            return persistentEntity;
        }

        @Override
        public Predicate predicate() {
            return predicate;
        }

        @Override
        public Collection<JoinPath> getJoinPaths() {
            return Collections.unmodifiableCollection(joinPaths.values());
        }

        @Override
        public Optional<JoinPath> getJoinPath(String path) {
            if (path != null) {
                return Optional.ofNullable(joinPaths.get(path));
            }
            return Optional.empty();
        }

    }

}
