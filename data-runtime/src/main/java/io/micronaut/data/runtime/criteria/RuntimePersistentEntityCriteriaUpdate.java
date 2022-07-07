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
package io.micronaut.data.runtime.criteria;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelPredicateVisitor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.criteria.metamodel.StaticMetamodelInitializer;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Internal
final class RuntimePersistentEntityCriteriaUpdate<T> extends AbstractPersistentEntityCriteriaUpdate<T> {

    private final AbstractCriteriaBuilder criteriaBuilder;
    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final StaticMetamodelInitializer staticMetamodelInitializer;

    public RuntimePersistentEntityCriteriaUpdate(AbstractCriteriaBuilder criteriaBuilder,
                                                 Class<T> root,
                                                 RuntimeEntityRegistry runtimeEntityRegistry,
                                                 StaticMetamodelInitializer staticMetamodelInitializer) {
        this.criteriaBuilder = criteriaBuilder;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.staticMetamodelInitializer = staticMetamodelInitializer;
        from(root);
    }

    @Override
    public PersistentEntityRoot<T> from(Class<T> entityClass) {
        return from(runtimeEntityRegistry.getEntity(entityClass));
    }

    @Override
    public PersistentEntityRoot<T> from(PersistentEntity persistentEntity) {
        RuntimePersistentEntity<T> runtimePersistentEntity = (RuntimePersistentEntity<T>) persistentEntity;
        if (entityRoot != null && !entityRoot.getJavaType().equals(runtimePersistentEntity.getIntrospection().getBeanType())) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        staticMetamodelInitializer.initializeMetadata(runtimePersistentEntity);
        RuntimePersistentEntityRoot<T> newEntityRoot = new RuntimePersistentEntityRoot<>(runtimePersistentEntity);
        entityRoot = newEntityRoot;
        return newEntityRoot;
    }

    @Override
    protected void setValue(String attributeName, Object value) {
        super.setValue(attributeName, asParameter(value));
    }

    @NotNull
    private ParameterExpression<?> asParameter(Object exp) {
        if (exp instanceof ParameterExpression) {
            return (ParameterExpression<?>) exp;
        }
        Objects.requireNonNull(exp);
        Class<Object> type;
        Object value;
        if (exp instanceof LiteralExpression) {
            LiteralExpression literalExpression = (LiteralExpression<?>) exp;
            type = literalExpression.getJavaType();
            value = literalExpression.getValue();
        } else if (exp instanceof Expression) {
            throw new IllegalStateException("Unexpected expression!");
        } else {
            type = (Class<Object>) exp.getClass();
            value = exp;
        }
        return criteriaBuilder.parameter(type, null, value);
    }

    @Override
    protected QueryModelPredicateVisitor createPredicateVisitor(QueryModel queryModel) {
        return new LiteralsAsParametersQueryModelPredicateVisitor(criteriaBuilder, queryModel);
    }

}
