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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The abstract implementation of {@link PersistentEntityCriteriaDelete}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPersistentEntityCriteriaDelete<T> implements PersistentEntityCriteriaDelete<T> {

    @Nullable
    protected Predicate predicate;
    @Nullable
    protected PersistentEntityRoot<T> entityRoot;
    @Nullable
    protected Selection<?> returning;

    @Override
    public PersistentEntity getPersistentEntity() {
        Objects.requireNonNull(entityRoot);
        return entityRoot.getPersistentEntity();
    }

    @Override
    public QueryResult build(AnnotationMetadata annotationMetadata, QueryBuilder queryBuilder) {
        Objects.requireNonNull(entityRoot);
        return queryBuilder.buildDelete(annotationMetadata,
            new DeleteQueryDefinitionImpl(entityRoot.getPersistentEntity(), predicate, returning));
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
        if (restriction instanceof ConjunctionPredicate conjunctionPredicate) {
            predicate = conjunctionPredicate;
        } else {
            predicate = new ConjunctionPredicate(Collections.singleton((IExpression<Boolean>) restriction));
        }
        return this;
    }

    @Override
    public PersistentEntityCriteriaDelete<T> where(Predicate... restrictions) {
        Objects.requireNonNull(restrictions);
        if (restrictions.length > 0) {
            predicate = restrictions.length == 1 ? restrictions[0] : new ConjunctionPredicate(Arrays.stream(restrictions).sequential().map(x -> (IExpression<Boolean>) x).toList());
        } else {
            predicate = null;
        }
        return this;
    }

    @Override
    public PersistentEntityRoot<T> getRoot() {
        Objects.requireNonNull(entityRoot);
        return entityRoot;
    }

    @Override
    @Nullable
    public Predicate getRestriction() {
        return predicate;
    }

    @Override
    public <U> PersistentEntitySubquery<U> subquery(ExpressionType<U> type) {
        throw notSupportedOperation();
    }

    @Override
    public <U> PersistentEntitySubquery<U> subquery(EntityType<U> type) {
        throw notSupportedOperation();
    }

    public final boolean hasVersionRestriction() {
        if (predicate == null) {
            return false;
        }
        Objects.requireNonNull(entityRoot);
        if (!entityRoot.getPersistentEntity().hasVersion()) {
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
    public PersistentEntityCriteriaDelete<T> returningMulti(Selection<?>... selections) {
        Objects.requireNonNull(selections);
        if (selections.length != 0) {
            this.returning = new CompoundSelection<>(List.of(selections));
        } else {
            this.returning = null;
        }
        return this;
    }

    @Override
    public Set<ParameterExpression<?>> getParameters() {
        return CriteriaUtils.extractPredicateParameters(predicate);
    }

    private static final class DeleteQueryDefinitionImpl extends AbstractPersistentEntityCriteriaQuery.BaseQueryDefinitionImpl implements QueryBuilder.DeleteQueryDefinition {

        @Nullable
        private final Selection<?> returningSelection;

        DeleteQueryDefinitionImpl(PersistentEntity persistentEntity, @Nullable Predicate predicate, @Nullable Selection<?> returningSelection) {
            super(persistentEntity, predicate, Map.of());
            this.returningSelection = returningSelection;
        }

        @Nullable
        @Override
        public Selection<?> returningSelection() {
            return returningSelection;
        }
    }
}
