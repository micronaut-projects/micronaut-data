/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaInsert;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.criteria.metamodel.StaticMetamodelInitializer;

/**
 * The runtime criteria insert.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class RuntimePersistentEntityCriteriaInsert<T> extends AbstractPersistentEntityCriteriaInsert<T> {

    private final AbstractCriteriaBuilder criteriaBuilder;
    private final StaticMetamodelInitializer staticMetamodelInitializer;

    RuntimePersistentEntityCriteriaInsert(AbstractCriteriaBuilder criteriaBuilder,
                                          Class<T> root,
                                          RuntimeEntityRegistry runtimeEntityRegistry,
                                          StaticMetamodelInitializer staticMetamodelInitializer) {
        this.criteriaBuilder = criteriaBuilder;
        this.staticMetamodelInitializer = staticMetamodelInitializer;
        from(runtimeEntityRegistry.getEntity(root));
    }

    private PersistentEntityRoot<T> from(RuntimePersistentEntity<T> runtimePersistentEntity) {
        if (entityRoot != null && !entityRoot.getJavaType().equals(runtimePersistentEntity.getIntrospection().getBeanType())) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        staticMetamodelInitializer.initializeMetadata(runtimePersistentEntity);
        RuntimePersistentEntityRoot<T> newEntityRoot = new RuntimePersistentEntityRoot<>(runtimePersistentEntity, criteriaBuilder);
        entityRoot = newEntityRoot;
        return newEntityRoot;
    }

}
