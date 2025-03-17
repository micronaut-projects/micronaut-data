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
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.jpa.criteria.impl.util.Joiner;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
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
 * @param <T>    The type of the result
 * @param <Self> The self type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPersistentEntityQuery<T, Self extends PersistentEntityQuery<T>> implements AbstractQuery<T>,
    QueryResultPersistentEntityCriteriaQuery, PersistentEntityQuery<T> {

    protected Map<String, Integer> parametersInRole = new LinkedHashMap<>();
    protected final CriteriaBuilder criteriaBuilder;
    protected final ExpressionType<T> resultType;
    protected Predicate predicate;
    protected Selection<?> selection;
    protected PersistentEntityRoot<?> entityRoot;
    protected List<Order> orders;
    protected int max = -1;
    protected int offset = 0;
    protected boolean forUpdate;
    protected boolean distinct;

    protected AbstractPersistentEntityQuery(ExpressionType<T> resultType, CriteriaBuilder criteriaBuilder) {
        this.resultType = resultType;
        this.criteriaBuilder = criteriaBuilder;
    }

    public final Map<String, Integer> getParametersInRole() {
        return parametersInRole;
    }

    /**
     * @return The self instance
     */
    protected abstract Self self();

    @Override
    public QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryBuilder2 queryBuilder) {
        return queryBuilder.buildSelect(annotationMetadata, toSelectQueryDefinition());
    }

    /**
     * @return Build {@link io.micronaut.data.model.query.builder.QueryBuilder2.SelectQueryDefinition}.
     */
    public QueryBuilder2.SelectQueryDefinition toSelectQueryDefinition() {
        return new SelectQueryDefinitionImpl(
            entityRoot,
            entityRoot.getPersistentEntity(),
            predicate,
            selection == null ? entityRoot : selection,
            calculateJoins(entityRoot.getPersistentEntity()),
            forUpdate,
            distinct,
            orders == null ? List.of() : orders,
            max,
            offset,
            parametersInRole
        );
    }

    private Map<String, JoinPath> calculateJoins(PersistentEntity persistentEntity) {
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
            Joiner.Joined joined = e.getValue();
            Join.Type joinType = calculateJoinType(joined);
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
            JoinPath jp = new JoinPath(path, associationPath, joinType, joined.getAlias());
            joinPaths.put(e.getKey(), jp);
        }
        return joinPaths;
    }

    private Join.Type calculateJoinType(Joiner.Joined joined) {
        Join.Type joinType = Optional.ofNullable(joined.getType()).orElse(Join.Type.DEFAULT);
        if (!isProjected(joined.getAssociation())) {
            // Fetch joins don't make sense if the entity is not projected
            joinType = switch (joinType) {
                case INNER, FETCH -> Join.Type.DEFAULT;
                case LEFT_FETCH -> Join.Type.LEFT;
                case RIGHT_FETCH -> Join.Type.RIGHT;
                default -> joinType;
            };
        }
        return joinType;
    }

    private boolean isProjected(jakarta.persistence.criteria.Join<?, ?> join) {
        return isProjected(selection, join);
    }

    private boolean isProjected(Selection<?> selection, jakarta.persistence.criteria.Join<?, ?> join) {
        if (selection instanceof CompoundSelection<?> compoundSelection) {
            // Avoid this check for now, we would need to support equals of different property paths
            return true;
//            for (Selection<?> compoundSelectionItem : compoundSelection.getCompoundSelectionItems()) {
//                if (isProjected(compoundSelectionItem, join)) {
//                    return true;
//                }
//            }
//            return false;
        }
        var root = selection == null ? entityRoot : selection;
        while (join != null) {
            if (join == root) {
                return true;
            }
            From<?, ?> parent = join.getParent();
            if (parent == root) {
                return true;
            }
            if (parent instanceof jakarta.persistence.criteria.Join<?, ?> nextJoin) {
                join = nextJoin;
            } else {
                return false;
            }
        }
        return false;
    }

    @Override
    public abstract <X> PersistentEntityRoot<X> from(Class<X> entityClass);

    @Override
    public <X> PersistentEntityRoot<X> from(EntityType<X> entity) {
        if (entityRoot != null) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        return null;
    }

    public abstract <X> PersistentEntityRoot<X> from(PersistentEntity persistentEntity);

    /**
     * Sets the max rows.
     *
     * @param limit The max ros
     * @return self
     */
    public Self limit(int limit) {
        this.max = limit;
        return self();
    }

    /**
     * Sets the offset.
     *
     * @param offset The offset
     * @return self
     */
    public Self offset(int offset) {
        this.offset = offset;
        return self();
    }

    /**
     * Enables the query for update.
     *
     * @param forUpdate The for update
     * @return self
     */
    public Self forUpdate(boolean forUpdate) {
        this.forUpdate = forUpdate;
        return self();
    }

    @Override
    public Self where(Expression<Boolean> restriction) {
        if (restriction instanceof ConjunctionPredicate conjunctionPredicate) {
            predicate = conjunctionPredicate;
        } else {
            predicate = new ConjunctionPredicate(Collections.singleton((IExpression<Boolean>) restriction));
        }
        return self();
    }

    @Override
    public Self where(Predicate... restrictions) {
        Objects.requireNonNull(restrictions);
        if (restrictions.length > 0) {
            predicate = restrictions.length == 1 ? restrictions[0] : new ConjunctionPredicate(
                Arrays.stream(restrictions).sequential().map(x -> (IExpression<Boolean>) x).toList()
            );
        } else {
            predicate = null;
        }
        return self();
    }

    @Override
    public Self groupBy(Expression<?>... grouping) {
        throw notSupportedOperation();
    }

    @Override
    public Self groupBy(List<Expression<?>> grouping) {
        throw notSupportedOperation();
    }

    @Override
    public Self having(Expression<Boolean> restriction) {
        throw notSupportedOperation();
    }

    @Override
    public Self having(Predicate... restrictions) {
        throw notSupportedOperation();
    }

    @Override
    public Self distinct(boolean distinct) {
        this.distinct = distinct;
        return self();
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
        return resultType.getJavaType();
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
        if (predicate instanceof BinaryPredicate binaryPredicate) {
            if (binaryPredicate.getLeftExpression() instanceof PersistentPropertyPath<?> pp) {
                return pp.getProperty() == pp.getProperty().getOwner().getIdentity();
            }
            if (binaryPredicate.getRightExpression() instanceof PersistentPropertyPath<?> pp) {
                return pp.getProperty() == pp.getProperty().getOwner().getIdentity();
            }
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

        private final Root<?> root;
        private final Selection<?> selection;
        private final boolean isForUpdate;
        private final boolean isDistinct;
        private final List<Order> order;
        private final int limit;
        private final int offset;
        private final Map<String, Integer> parametersInRole;

        public SelectQueryDefinitionImpl(Root<?> root,
                                         PersistentEntity persistentEntity,
                                         Predicate predicate,
                                         Selection<?> selection,
                                         Map<String, JoinPath> joinPaths,
                                         boolean isForUpdate,
                                         boolean isDistinct,
                                         List<Order> order,
                                         int limit,
                                         int offset,
                                         Map<String, Integer> parametersInRole) {
            super(persistentEntity, predicate, joinPaths);
            this.root = root;
            this.selection = selection;
            this.isForUpdate = isForUpdate;
            this.isDistinct = isDistinct;
            this.order = order;
            this.limit = limit;
            this.offset = offset;
            this.parametersInRole = parametersInRole;
        }

        @Override
        public Map<String, Integer> parametersInRole() {
            return parametersInRole;
        }

        @Override
        public Root<?> root() {
            return root;
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
