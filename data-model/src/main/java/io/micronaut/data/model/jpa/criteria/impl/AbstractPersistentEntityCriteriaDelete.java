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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelPredicateVisitor;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelSelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.jpa.criteria.impl.util.Joiner;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.EntityType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The abstract implementation of {@link PersistentEntityCriteriaDelete}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPersistentEntityCriteriaDelete<T> implements PersistentEntityCriteriaDelete<T>,
    QueryResultPersistentEntityCriteriaQuery {

    protected Predicate predicate;
    protected PersistentEntityRoot<T> entityRoot;
    protected Selection<?> returning;

    @NonNull
    @Override
    public QueryModel getQueryModel() {
        if (entityRoot == null) {
            throw new IllegalStateException("The root entity must be specified!");
        }
        QueryModel qm = QueryModel.from(entityRoot.getPersistentEntity());
        Joiner joiner = new Joiner();
        if (predicate instanceof PredicateVisitable predicateVisitable) {
            predicateVisitable.accept(createPredicateVisitor(qm));
            predicateVisitable.accept(joiner);
        }
        if (returning instanceof SelectionVisitable selectionVisitable) {
            selectionVisitable.accept(new QueryModelSelectionVisitor(qm, false));
            selectionVisitable.accept(joiner);
        }
        for (Map.Entry<String, Joiner.Joined> e : joiner.getJoins().entrySet()) {
            qm.join(e.getKey(), Optional.ofNullable(e.getValue().getType()).orElse(Join.Type.DEFAULT), e.getValue().getAlias());
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
    public QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryBuilder queryBuilder) {
        return queryBuilder.buildDelete(annotationMetadata, getQueryModel());
    }

    @Override
    public QueryResult buildQuery(AnnotationMetadata annotationMetadata, QueryBuilder2 queryBuilder) {
        return queryBuilder.buildDelete(
            annotationMetadata,
            new DeleteQueryDefinitionImpl(entityRoot.getPersistentEntity(), predicate, returning)
        );
    }

    @Override
    public abstract PersistentEntityRoot<T> from(Class<T> entityClass);

    @Override
    public abstract PersistentEntityRoot<T> from(PersistentEntity persistentEntity);

    @Override
    public PersistentEntityRoot<T> from(EntityType<T> entity) {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public PersistentEntityCriteriaDelete<T> where(Expression<Boolean> restriction) {
        predicate = new ConjunctionPredicate(Collections.singleton((IExpression<Boolean>) restriction));
        return this;
    }

    @Override
    public PersistentEntityCriteriaDelete<T> where(Predicate... restrictions) {
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
    public PersistentEntityRoot<T> getRoot() {
        return entityRoot;
    }

    @Override
    public Predicate getRestriction() {
        return predicate;
    }

    @Override
    public <U> Subquery<U> subquery(Class<U> type) {
        throw new IllegalStateException("Unsupported!");
    }

    public final boolean hasVersionRestriction() {
        if (entityRoot.getPersistentEntity().getVersion() == null) {
            return false;
        }
        return CriteriaUtils.hasVersionPredicate(predicate);
    }

    @Override
    public PersistentEntityCriteriaDelete<T> returning(Selection<? extends T> selection) {
        Objects.requireNonNull(selection);
        this.returning = selection;
        return this;
    }

    @Override
    public PersistentEntityCriteriaDelete<T> returningMulti(List<Selection<?>> selectionList) {
        Objects.requireNonNull(selectionList);
        if (!selectionList.isEmpty()) {
            this.returning = new CompoundSelection<>(selectionList);
        } else {
            this.returning = null;
        }
        return this;
    }

    @Override
    public PersistentEntityCriteriaDelete<T> returningMulti(@NonNull Selection<?>... selections) {
        Objects.requireNonNull(selections);
        if (selections.length != 0) {
            this.returning = new CompoundSelection<>(List.of(selections));
        } else {
            this.returning = null;
        }
        return this;
    }

    private static final class DeleteQueryDefinitionImpl extends AbstractPersistentEntityCriteriaQuery.BaseQueryDefinitionImpl implements QueryBuilder2.DeleteQueryDefinition {

        private final Selection<?> returningSelection;

        DeleteQueryDefinitionImpl(PersistentEntity persistentEntity, Predicate predicate, Selection<?> returningSelection) {
            super(persistentEntity, predicate);
            this.returningSelection = returningSelection;
        }

        @Override
        public Selection<?> returningSelection() {
            return returningSelection;
        }
    }
}
