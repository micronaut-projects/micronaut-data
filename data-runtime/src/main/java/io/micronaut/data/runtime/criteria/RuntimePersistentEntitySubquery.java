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
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.expression.ClassExpressionType;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.criteria.metamodel.StaticMetamodelInitializer;
import jakarta.persistence.criteria.AbstractQuery;

/**
 * The runtime subquery.
 *
 * @param <T> The result type
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
final class RuntimePersistentEntitySubquery<T> extends AbstractPersistentEntitySubquery<T> {

    private final AbstractCriteriaBuilder criteriaBuilder;
    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final StaticMetamodelInitializer staticMetamodelInitializer;

    public RuntimePersistentEntitySubquery(AbstractQuery<?> parent,
                                           AbstractCriteriaBuilder criteriaBuilder,
                                           StaticMetamodelInitializer staticMetamodelInitializer,
                                           Class<T> resultType,
                                           RuntimeEntityRegistry runtimeEntityRegistry) {
        super(parent, new ClassExpressionType<>(resultType), criteriaBuilder);
        this.criteriaBuilder = criteriaBuilder;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.staticMetamodelInitializer = staticMetamodelInitializer;
    }

    @Override
    public <X> PersistentEntityRoot<X> from(Class<X> entityClass) {
        return from(runtimeEntityRegistry.getEntity(entityClass));
    }

    @Override
    public <X> PersistentEntityRoot<X> from(PersistentEntity persistentEntity) {
        if (entityRoot != null) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        RuntimePersistentEntity<X> runtimePersistentEntity = (RuntimePersistentEntity<X>) persistentEntity;
        staticMetamodelInitializer.initializeMetadata(runtimePersistentEntity);
        RuntimePersistentEntityRoot<X> newEntityRoot = new RuntimePersistentEntityRoot<>(this, runtimePersistentEntity, criteriaBuilder);
        entityRoot = newEntityRoot;
        return newEntityRoot;
    }

    @Override
    public <U> PersistentEntitySubquery<U> subquery(ExpressionType<U> type) {
        return new RuntimePersistentEntitySubquery<>(this, criteriaBuilder, staticMetamodelInitializer, type.getJavaType(), runtimeEntityRegistry);
    }
}
