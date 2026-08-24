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
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityQuery.BaseQueryDefinitionImpl;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.GeneratedEntityUpdateQueryDefinition;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireParameter;
import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;

/**
 * The abstract implementation of {@link PersistentEntityCriteriaUpdate}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPersistentEntityCriteriaUpdate<T> implements PersistentEntityCriteriaUpdate<T> {

    @Nullable
    protected Predicate predicate;
    @Nullable
    protected PersistentEntityRoot<T> entityRoot;
    protected Map<String, Object> updateValues = new LinkedHashMap<>();
    @Nullable
    protected Selection<?> returning;
    // Repository entity updates need separate SQL handling for @Reservable assignments.
    // Explicit Criteria API updates leave this false and are validated as direct updates.
    private boolean generatedEntityUpdate;

    @Override
    public PersistentEntity getPersistentEntity() {
        Objects.requireNonNull(entityRoot);
        return entityRoot.getPersistentEntity();
    }

    @Override
    public QueryResult build(AnnotationMetadata annotationMetadata, QueryBuilder queryBuilder) {
        Objects.requireNonNull(entityRoot);
        return queryBuilder.buildUpdate(annotationMetadata,
            new UpdateQueryDefinitionImpl(entityRoot.getPersistentEntity(), predicate, returning, updateValues, generatedEntityUpdate));
    }

    @Override
    public abstract PersistentEntityRoot<T> from(Class<T> entityClass);

    @Override
    public abstract PersistentEntityRoot<T> from(PersistentEntity persistentEntity);

    @Override
    public PersistentEntityRoot<T> from(EntityType<T> entity) {
        throw notSupportedOperation();
    }

    @Override
    public PersistentEntityRoot<T> getRoot() {
        Objects.requireNonNull(entityRoot);
        return entityRoot;
    }

    @Override
    public <Y, X extends Y> PersistentEntityCriteriaUpdate<T> set(SingularAttribute<? super T, Y> attribute, @Nullable X value) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> PersistentEntityCriteriaUpdate<T> set(SingularAttribute<? super T, Y> attribute, Expression<? extends Y> value) {
        throw notSupportedOperation();
    }

    @Override
    public <Y, X extends Y> PersistentEntityCriteriaUpdate<T> set(Path<Y> attribute, @Nullable X value) {
        setValue(requireProperty(attribute).getPathAsString(), value);
        return this;
    }

    @Override
    public <Y> PersistentEntityCriteriaUpdate<T> set(Path<Y> attribute, Expression<? extends Y> value) {
        setValue(requireProperty(attribute).getPathAsString(), requireParameter(value));
        return this;
    }

    @Override
    public PersistentEntityCriteriaUpdate<T> set(String attributeName, @Nullable Object value) {
        setValue(attributeName, value);
        return this;
    }

    /**
     * Set update value.
     *
     * @param attributeName The attribute name
     * @param value         The value
     */
    protected void setValue(String attributeName, @Nullable Object value) {
        updateValues.put(attributeName, value);
    }

    @Override
    public PersistentEntityCriteriaUpdate<T> where(Expression<Boolean> restriction) {
        if (restriction instanceof ConjunctionPredicate conjunctionPredicate) {
            predicate = conjunctionPredicate;
        } else {
            predicate = new ConjunctionPredicate(Collections.singleton((IExpression<Boolean>) restriction));
        }
        return this;
    }

    @Override
    public PersistentEntityCriteriaUpdate<T> where(Predicate... restrictions) {
        Objects.requireNonNull(restrictions);
        if (restrictions.length > 0) {
            predicate = restrictions.length == 1 ? restrictions[0] : new ConjunctionPredicate(Arrays.stream(restrictions).sequential().map(x -> (IExpression<Boolean>) x).toList());
        } else {
            predicate = null;
        }
        return this;
    }

    @Override
    @Nullable
    public final Predicate getRestriction() {
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

    public final Map<String, Object> getUpdateValues() {
        return updateValues;
    }

    /**
     * Mark this internal criteria representation as generated from a repository entity update
     * such as {@code update(entity)} or the update branch of {@code save(entity)}.
     *
     * <p>This is not a marker for arbitrary Criteria API updates.</p>
     */
    public void markGeneratedEntityUpdate() {
        generatedEntityUpdate = true;
    }

    @Override
    public Set<ParameterExpression<?>> getParameters() {
        return CriteriaUtils.extractPredicateParameters(predicate);
    }

    @Override
    public PersistentEntityCriteriaUpdate<T> returning(Selection<? extends T> selection) {
        Objects.requireNonNull(selection);
        this.returning = selection;
        return this;
    }

    @Override
    public PersistentEntityCriteriaUpdate<T> returningMulti(List<Selection<?>> selectionList) {
        Objects.requireNonNull(selectionList);
        if (!selectionList.isEmpty()) {
            this.returning = new CompoundSelection<>(selectionList);
        } else {
            this.returning = null;
        }
        return this;
    }

    private static final class UpdateQueryDefinitionImpl extends BaseQueryDefinitionImpl implements GeneratedEntityUpdateQueryDefinition {

        private final Map<String, Object> propertiesToUpdate;
        private final boolean generatedEntityUpdate;
        @Nullable
        private final Selection<?> returningSelection;

        private UpdateQueryDefinitionImpl(PersistentEntity persistentEntity,
                                         @Nullable
                                         Predicate predicate,
                                         @Nullable
                                         Selection<?> returningSelection,
                                         Map<String, Object> propertiesToUpdate,
                                         boolean generatedEntityUpdate) {
            super(persistentEntity, predicate, Map.of());
            this.propertiesToUpdate = propertiesToUpdate;
            this.generatedEntityUpdate = generatedEntityUpdate;
            this.returningSelection = returningSelection;
        }

        @Override
        public Map<String, Object> propertiesToUpdate() {
            return propertiesToUpdate;
        }

        @Override
        public boolean isGeneratedEntityUpdate() {
            return generatedEntityUpdate;
        }

        @Override
        @Nullable
        public Selection<?> returningSelection() {
            return returningSelection;
        }
    }
}
