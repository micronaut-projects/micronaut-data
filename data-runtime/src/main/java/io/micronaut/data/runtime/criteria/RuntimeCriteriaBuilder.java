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

import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.runtime.criteria.metamodel.StaticMetamodelInitializer;
import jakarta.inject.Singleton;

/**
 * The runtime implementation of {@link AbstractCriteriaBuilder}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Singleton
public class RuntimeCriteriaBuilder extends AbstractCriteriaBuilder {

    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final StaticMetamodelInitializer staticMetamodelInitializer = new StaticMetamodelInitializer();

    public RuntimeCriteriaBuilder(RuntimeEntityRegistry runtimeEntityRegistry) {
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public PersistentEntityCriteriaQuery<Object> createQuery() {
        return new RuntimePersistentEntityCriteriaQuery<>(runtimeEntityRegistry, staticMetamodelInitializer, Object.class);
    }

    @Override
    public <T> PersistentEntityCriteriaQuery<T> createQuery(Class<T> resultClass) {
        return new RuntimePersistentEntityCriteriaQuery<>(runtimeEntityRegistry, staticMetamodelInitializer, resultClass);
    }

    @Override
    public <T> PersistentEntityCriteriaUpdate<T> createCriteriaUpdate(Class<T> targetEntity) {
        return new RuntimePersistentEntityCriteriaUpdate<>(runtimeEntityRegistry, targetEntity, staticMetamodelInitializer);
    }

    @Override
    public <T> PersistentEntityCriteriaDelete<T> createCriteriaDelete(Class<T> targetEntity) {
        return new RuntimePersistentEntityCriteriaDelete<>(runtimeEntityRegistry, targetEntity, staticMetamodelInitializer);
    }
}
