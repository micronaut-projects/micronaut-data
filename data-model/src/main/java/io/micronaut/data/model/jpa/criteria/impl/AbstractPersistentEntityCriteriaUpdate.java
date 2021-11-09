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

import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelPredicateVisitor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
public abstract class AbstractPersistentEntityCriteriaUpdate<T> implements PersistentEntityCriteriaUpdate<T>,
        QueryResultPersistentEntityCriteriaQuery {

    protected Predicate predicate;
    protected PersistentEntityRoot<T> entityRoot;
    protected Map<String, Object> updateValues = new LinkedHashMap<>();

    @Override
    public QueryResult buildQuery(QueryBuilder queryBuilder) {
        return queryBuilder.buildUpdate(getQueryModel(), updateValues);
    }

    @Override
    @NotNull
    public QueryModel getQueryModel() {
        if (entityRoot == null) {
            throw new IllegalStateException("The root entity must be specified!");
        }
        QueryModel qm = QueryModel.from(entityRoot.getPersistentEntity());
        if (predicate instanceof PredicateVisitable) {
            ((PredicateVisitable) predicate).accept(new QueryModelPredicateVisitor(qm));
        }
        join(qm, (AbstractPersistentEntityJoinSupport<?, ?>) entityRoot);
        return qm;
    }

    private void join(QueryModel qm, AbstractPersistentEntityJoinSupport<?, ?> joinEntityRoot) {
        for (AbstractPersistentEntityJoinSupport.Joined join : joinEntityRoot.getJoinsInternal()) {
            qm.join(join.getAssociation().getPathAsString(), join.getType(), join.getAlias());
            join(qm, (AbstractPersistentEntityJoinSupport<?, ?>) join.getAssociation());
        }
    }

    @Override
    public abstract PersistentEntityRoot<T> from(Class<T> entityClass);

    @Override
    public abstract PersistentEntityRoot<T> from(PersistentEntity persistentEntity);

    @Override
    public PersistentEntityRoot<T> from(EntityType<T> entity) {
        if (entityRoot != null) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        return null;
    }

    @Override
    public PersistentEntityRoot<T> getRoot() {
        return entityRoot;
    }

    @Override
    public <Y, X extends Y> PersistentEntityCriteriaUpdate<T> set(SingularAttribute<? super T, Y> attribute, X value) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> PersistentEntityCriteriaUpdate<T> set(SingularAttribute<? super T, Y> attribute, Expression<? extends Y> value) {
        throw notSupportedOperation();
    }

    @Override
    public <Y, X extends Y> PersistentEntityCriteriaUpdate<T> set(Path<Y> attribute, X value) {
        setValue(requireProperty(attribute).getProperty().getName(), value);
        return this;
    }

    @Override
    public <Y> PersistentEntityCriteriaUpdate<T> set(Path<Y> attribute, Expression<? extends Y> value) {
        setValue(requireProperty(attribute).getProperty().getName(), requireParameter(value));
        return this;
    }

    @Override
    public PersistentEntityCriteriaUpdate<T> set(String attributeName, Object value) {
        setValue(attributeName, value);
        return this;
    }

    /**
     * Set update value.
     *
     * @param attributeName The attribute name
     * @param value         The value
     */
    protected void setValue(String attributeName, Object value) {
        updateValues.put(attributeName, value);
    }

    @Override
    public PersistentEntityCriteriaUpdate<T> where(Expression<Boolean> restriction) {
        // TODO: bind parameters
        predicate = new ConjunctionPredicate(Collections.singleton((IExpression<Boolean>) restriction));
        return this;
    }

    @Override
    public PersistentEntityCriteriaUpdate<T> where(Predicate... restrictions) {
        // TODO: bind parameters
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
    public final Predicate getRestriction() {
        return predicate;
    }

    @Override
    public <U> Subquery<U> subquery(Class<U> type) {
        throw notSupportedOperation();
    }

    public final boolean hasVersionRestriction() {
        if (entityRoot.getPersistentEntity().getVersion() == null) {
            return false;
        }
        return CriteriaUtils.hasVersionPredicate(predicate);
    }

    public final Map<String, Object> getUpdateValues() {
        return updateValues;
    }

    @Override
    public Set<ParameterExpression<?>> getParameters() {
        return CriteriaUtils.extractPredicateParameters(predicate);
    }
}
