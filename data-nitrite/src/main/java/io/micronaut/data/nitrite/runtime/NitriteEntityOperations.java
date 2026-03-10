/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntityOperations;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.filters.Filter;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Internal entity operations for Nitrite with automatic event firing and version handling.
 *
 * @param <T> The entity type
 * @since 4.14.0
 */
@Internal
public final class NitriteEntityOperations<T> extends AbstractSyncEntityOperations<NitriteOperationContext, T, RuntimeException> {

    private final NitriteCollection collection;
    private final NitriteEntityMapper entityMapper;
    private final SyncCascadeOperations<NitriteOperationContext> cascadeOperations;
    private final NitriteOperationsHelper helper;

    public NitriteEntityOperations(
            NitriteOperationContext ctx,
            SyncCascadeOperations<NitriteOperationContext> cascadeOperations,
            EntityEventListener<Object> entityEventListener,
            RuntimePersistentEntity<T> persistentEntity,
            ConversionService conversionService,
            NitriteEntityMapper entityMapper,
            NitriteOperationsHelper helper,
            T entity,
            boolean insert) {
        super(ctx, cascadeOperations, entityEventListener, persistentEntity, conversionService, entity, insert);
        this.cascadeOperations = cascadeOperations;
        this.entityMapper = entityMapper;
        this.helper = helper;
        this.collection = helper.getCollection(persistentEntity.getIntrospection().getBeanType());
    }

    @Override
    protected void cascadePre(Relation.Cascade cascadeType) {
        if (insert && ctx.persisted.contains(entity)) {
            return;
        }
        entity = cascadeOperations.cascadeEntity(ctx, entity, persistentEntity, false, cascadeType);
    }

    @Override
    protected void cascadePost(Relation.Cascade cascadeType) {
        if (insert && ctx.persisted.contains(entity)) {
            return;
        }
        entity = cascadeOperations.cascadeEntity(ctx, entity, persistentEntity, true, cascadeType);
    }

    @Override
    protected void collectAutoPopulatedPreviousValues() {
    }

    @Override
    public T getEntity() {
        return entity;
    }

    @Override
    protected void execute() throws RuntimeException {
        // Skip if already persisted in this context
        if (insert && ctx.persisted.contains(entity)) {
            return;
        }
        
        Class<T> type = (Class<T>) persistentEntity.getIntrospection().getBeanType();
        if (insert) {
            helper.generateIdIfNecessary(entity, type);
            // Initialize version to 0 if not set (for optimistic locking)
            if (persistentEntity.getVersion() != null) {
                BeanProperty<T, Object> versionProperty = (BeanProperty<T, Object>) persistentEntity.getVersion().getProperty();
                if (versionProperty.get(entity) == null) {
                    entity = helper.updateEntityId(versionProperty, entity, 0L);
                }
            }
            Document doc = entityMapper.toDocument(entity);
            helper.logInsert(collection.getName(), doc);
            collection.insert(doc);
            ctx.persisted.add(entity);
        } else {
            Filter filter = entityMapper.idEqualsFilter(type, entityMapper.getEntityIdValue(entity, type));
            // Add version filter for optimistic locking
            if (persistentEntity.getVersion() != null) {
                BeanProperty<T, Object> versionProperty = (BeanProperty<T, Object>) persistentEntity.getVersion().getProperty();
                Object versionValue = versionProperty.get(entity);
                filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(persistentEntity.getVersion().getPersistedName()).eq(helper.toFilterValue(versionValue)));
                // Increment version
                long nextVersion = (versionValue == null ? 0L : ((Number) versionValue).longValue()) + 1;
                entity = helper.updateEntityId(versionProperty, entity, nextVersion);
            }
            Document update = entityMapper.toDocument(entity);
            helper.logUpdate(collection.getName(), filter, update);
            long rows = collection.update(filter, update, org.dizitart.no2.collection.UpdateOptions.updateOptions(false)).getAffectedCount();
            checkOptimisticLocking(1, rows);
        }
    }

    private void checkOptimisticLocking(int expected, long received) {
        if (persistentEntity.getVersion() != null && received != expected) {
            throw new io.micronaut.data.exceptions.OptimisticLockException("Execute update returned unexpected row count. Expected: " + expected + " got: " + received);
        }
    }

    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
        boolean vetoed = !fn.apply((EntityEventContext<Object>) event);
        if (vetoed) {
            return true;
        }
        T newEntity = event.getEntity();
        if (entity != newEntity) {
            entity = newEntity;
        }
        return false;
    }

    @Override
    protected void triggerPost(Consumer<EntityEventContext<Object>> fn) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
        fn.accept((EntityEventContext<Object>) event);
        T newEntity = event.getEntity();
        if (entity != newEntity) {
            entity = newEntity;
        }
    }
}
