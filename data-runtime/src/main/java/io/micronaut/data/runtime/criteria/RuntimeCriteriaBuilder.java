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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NextMajorVersion;
import org.jspecify.annotations.NonNull;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaInsert;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.criteria.metamodel.StaticMetamodelInitializer;
import io.micronaut.data.runtime.date.CurrentDateTimeProvider;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * The runtime implementation of {@link AbstractCriteriaBuilder}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Singleton
public final class RuntimeCriteriaBuilder extends AbstractCriteriaBuilder {

    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final StaticMetamodelInitializer staticMetamodelInitializer = new StaticMetamodelInitializer();

    public RuntimeCriteriaBuilder() {
        this(new RuntimeEntityRegistry() {

            private final Map<Class, RuntimePersistentEntity> map = new HashMap<>();

            @Override
            public EntityEventListener<Object> getEntityEventListener() {
                throw new IllegalStateException("EntityEventListeners are not yet supported");
            }

            @Override
            public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, @Nullable Object previousValue) {
                throw new IllegalStateException("AutoPopulateRuntimeProperty are not yet supported");
            }

            @Override
            public <T> RuntimePersistentEntity<T> getEntity(Class<T> type) {
                RuntimeEntityRegistry entityRegistry = this;
                return map.computeIfAbsent(type, t -> new RuntimePersistentEntity<Object>(t) {
                    @Override
                    protected RuntimePersistentEntity<Object> getEntity(Class<Object> type) {
                        return entityRegistry.getEntity(type);
                    }
                });
            }

            @Override
            public <T> RuntimePersistentEntity<T> newEntity(Class<T> type) {
                return new RuntimePersistentEntity<>(type);
            }

            @Override
            public @NonNull ApplicationContext getApplicationContext() {
                throw new IllegalStateException("ApplicationContext are not yet supported");
            }
        }, new CurrentDateTimeProvider());
    }

    public RuntimeCriteriaBuilder(RuntimeEntityRegistry runtimeEntityRegistry) {
        this(runtimeEntityRegistry, new CurrentDateTimeProvider());
    }

    @Inject
    public RuntimeCriteriaBuilder(RuntimeEntityRegistry runtimeEntityRegistry, DateTimeProvider<OffsetDateTime> dateTimeProvider) {
        super(dateTimeProvider::getNow);
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public PersistentEntityCriteriaQuery<Object> createQuery() {
        return new RuntimePersistentEntityCriteriaQuery<>(this, staticMetamodelInitializer, Object.class, runtimeEntityRegistry);
    }

    @Override
    public PersistentEntityCriteriaQuery<Tuple> createTupleQuery() {
        return new RuntimePersistentEntityCriteriaQuery<>(this, staticMetamodelInitializer, Tuple.class, runtimeEntityRegistry);
    }

    @Override
    public <T> PersistentEntityCriteriaQuery<T> createQuery(Class<T> resultClass) {
        return new RuntimePersistentEntityCriteriaQuery<>(this, staticMetamodelInitializer, resultClass, runtimeEntityRegistry);
    }

    @Override
    public <T> PersistentEntityCriteriaUpdate<T> createCriteriaUpdate(Class<T> targetEntity) {
        return new RuntimePersistentEntityCriteriaUpdate<>(this, targetEntity, runtimeEntityRegistry, staticMetamodelInitializer);
    }

    @Override
    public <T> PersistentEntityCriteriaDelete<T> createCriteriaDelete(Class<T> targetEntity) {
        return new RuntimePersistentEntityCriteriaDelete<>(this, targetEntity, runtimeEntityRegistry, staticMetamodelInitializer);
    }

    @Override
    public <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(Class<T> targetEntity) {
        return new RuntimePersistentEntityCriteriaInsert<>(this, targetEntity, runtimeEntityRegistry, staticMetamodelInitializer);
    }

    @Override
    @NextMajorVersion("Require non null")
    public <T> Expression<T> literal(@Nullable T value) {
        // Runtime literals need to be bind as parameters not modifying the query to avoid the SQL injection
        return super.parameter(value == null ? (Class<T>) Object.class : (Class<T>) value.getClass(), null, value);
    }

    /**
     * @return The runtime entity registry
     */
    public RuntimeEntityRegistry getRuntimeEntityRegistry() {
        return runtimeEntityRegistry;
    }
}
