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
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntityOperations;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations.SyncCascadeOperationsHelper;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.filters.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Internal entity operations for Nitrite with automatic event firing and version handling.
 * Uses ObjectRepositoryWriter for entity-to-Document conversion.
 * <p>
 * <b>Save (INSERT) Operation:</b> Uses upsert semantics:
 * <ul>
 *   <li>If the entity has no ID: generates an ID and inserts as a new document</li>
 *   <li>If the entity has an existing ID: updates (replaces) the document if found, or inserts if not found</li>
 * </ul>
 * This allows {@code save()} to work for both creating new entities and updating existing ones.
 * <p>
 * <b>Update (UPDATE) Operation:</b> Replaces an existing document by ID.
 * Requires the entity to have an ID; throws {@link OptimisticLockException} if version mismatch occurs.
 *
 * @param <T> The entity type
 * @since 4.14.0
 */
@Internal
public final class NitriteEntityOperations<T> extends AbstractSyncEntityOperations<NitriteOperationContext, T, RuntimeException> {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteEntityOperations.class);
    private final NitriteCollection collection;

    private final ObjectRepositoryWriter<T> repositoryWriter;
    private final NitriteEntityMapper entityMapper;
    private final SyncCascadeOperations<NitriteOperationContext> cascadeOperations;
    private final NitriteOperationsHelper helper;
    private final OperationType operationType;
    private Object preVersionValue;

    enum OperationType {
        INSERT, UPDATE, DELETE
    }

    /**
     * Creates a new NitriteEntityOperations.
     *
     * @param ctx the operation context
     * @param cascadeOperations the cascade operations
     * @param entityEventListener the entity event listener
     * @param persistentEntity the persistent entity
     * @param conversionService the conversion service
     * @param entityMapper the entity mapper
     * @param helper the operations helper
     * @param entity the entity to operate on
     * @param operationType the operation type
     */
    public NitriteEntityOperations(
            NitriteOperationContext ctx,
            SyncCascadeOperations<NitriteOperationContext> cascadeOperations,
            EntityEventListener<Object> entityEventListener,
            RuntimePersistentEntity<T> persistentEntity,
            ConversionService conversionService,
            NitriteEntityMapper entityMapper,
            NitriteOperationsHelper helper,
            T entity,
            OperationType operationType) {
        super(ctx, cascadeOperations, entityEventListener, persistentEntity, conversionService, entity, operationType == OperationType.INSERT);
        this.cascadeOperations = cascadeOperations;
        this.entityMapper = entityMapper;
        this.repositoryWriter = new ObjectRepositoryWriter<>(entityMapper, persistentEntity);
        this.helper = helper;
        this.collection = helper.getCollection(persistentEntity.getIntrospection().getBeanType());
        this.operationType = operationType;
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
    public void delete() {
        super.delete();
    }

    @Override
    protected void execute() throws RuntimeException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("execute: operationType={}, entity={}", operationType, entity);
        }
        // Skip if already persisted in this context
        if (operationType == OperationType.INSERT && ctx.persisted.contains(entity)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("execute: skipping INSERT because already persisted");
            }
            return;
        }

        Class<T> type = (Class<T>) persistentEntity.getIntrospection().getBeanType();
        if (operationType == OperationType.INSERT) {
            // Save operation uses upsert semantics:
            // - If entity has no ID: generate ID and insert as new document
            // - If entity has ID: update (replace) existing document, or insert if not found
            // This allows save() to work for both create and update scenarios
            Object entityId = persistentEntity.getIdentity() != null
                ? persistentEntity.getIdentity().getProperty().get(entity)
                : null;

            if (entityId != null) {
                // Entity has ID - use upsert (update with insert-if-absent)
                // Initialize version to 0 if not set (for optimistic locking)
                if (repositoryWriter.needsVersionInit(entity)) {
                    BeanProperty<T, Object> versionProperty = (BeanProperty<T, Object>) persistentEntity.getVersion().getProperty();
                    entity = helper.updateEntityId(versionProperty, entity, 0L);
                }
                Document doc = repositoryWriter.toDocument(entity);
                Filter filter = entityMapper.idEqualsFilter(type, entityId);
                helper.logUpdate(collection.getName(), filter, doc);
                long rows = collection.update(filter, doc, org.dizitart.no2.collection.UpdateOptions.updateOptions(true)).getAffectedCount();
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(1, rows);
                }
            } else {
                // No ID - generate and insert as new
                helper.generateIdIfNecessary(entity, type);
                // Initialize version to 0 if not set (for optimistic locking)
                if (repositoryWriter.needsVersionInit(entity)) {
                    BeanProperty<T, Object> versionProperty = (BeanProperty<T, Object>) persistentEntity.getVersion().getProperty();
                    entity = helper.updateEntityId(versionProperty, entity, 0L);
                }
                Document doc = repositoryWriter.toDocument(entity);
                helper.logInsert(collection.getName(), doc);
                collection.insert(doc);
                Object generatedId = doc.get("_id");
                if (generatedId != null && persistentEntity.getIdentity() != null && persistentEntity.getIdentity().getProperty().get(entity) == null) {
                    entity = helper.updateEntityId((BeanProperty<T, Object>) persistentEntity.getIdentity().getProperty(), entity, generatedId);
                }
            }
            ctx.persisted.add(entity);
        } else if (operationType == OperationType.UPDATE) {
            // Update operation: replace existing document by ID
            // Requires entity to have an ID; throws OptimisticLockException if version mismatch
            // Note: VersionGeneratingEntityEventListener.preUpdate() already incremented the version
            Filter filter = entityMapper.idEqualsFilter(type, entityMapper.getEntityIdValue(entity, type));
            if (persistentEntity.getVersion() != null) {
                Object versionValue = preVersionValue;
                if (versionValue == null) {
                    versionValue = persistentEntity.getVersion().getProperty().get(entity);
                }
                filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(persistentEntity.getVersion().getPersistedName()).eq(helper.toFilterValue(versionValue)));
            }
            Document update = repositoryWriter.toDocument(entity);
            helper.logUpdate(collection.getName(), filter, update);
            long rows = collection.update(filter, update, org.dizitart.no2.collection.UpdateOptions.updateOptions(false)).getAffectedCount();
            checkOptimisticLocking(1, rows);
        } else {
            // Delete operation
            Filter filter = entityMapper.idEqualsFilter(type, entityMapper.getEntityIdValue(entity, type));
            if (persistentEntity.getVersion() != null) {
                Object versionValue = preVersionValue;
                if (versionValue == null) {
                    versionValue = persistentEntity.getVersion().getProperty().get(entity);
                }
                filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(persistentEntity.getVersion().getPersistedName()).eq(helper.toFilterValue(versionValue)));
            }
            helper.logFind(collection.getName(), filter);
            long rows = collection.remove(filter, false).getAffectedCount();
            checkOptimisticLocking(1, rows);
        }
    }

    private void checkOptimisticLocking(int expected, long received) {
        if (persistentEntity.getVersion() != null && received != expected) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + expected + " got: " + received);
        }
    }

    @Override
    protected boolean triggerPrePersist() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("triggerPrePersist: entity={}", entity);
        }
        
        // Generate ID early so children can reference it
        Class<T> type = (Class<T>) persistentEntity.getIntrospection().getBeanType();
        helper.generateIdIfNecessary(entity, type);
        
        // Manual cascade to ensure events fire for children BEFORE parent
        for (io.micronaut.data.model.runtime.RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
            if (prop instanceof io.micronaut.data.model.runtime.RuntimeAssociation<?> assoc && assoc.doesCascade(io.micronaut.data.annotation.Relation.Cascade.ALL, io.micronaut.data.annotation.Relation.Cascade.PERSIST)) {
                Object value = prop.getProperty().get(entity);
                if (value instanceof Iterable<?> iterable) {
                    java.util.List<Object> list = new java.util.ArrayList<>();
                    for (Object o : iterable) {
                        list.add(o);
                    }
                    if (!list.isEmpty()) {
                        Class<Object> associatedType = (Class<Object>) assoc.getAssociatedEntity().getIntrospection().getBeanType();
                        ((SyncCascadeOperationsHelper<NitriteOperationContext>) helper).persistBatch(ctx, list, (io.micronaut.data.model.runtime.RuntimePersistentEntity<Object>) assoc.getAssociatedEntity(), null);
                    }
                } else if (value != null) {
                    ((SyncCascadeOperationsHelper<NitriteOperationContext>) helper).persistOne(ctx, value, (io.micronaut.data.model.runtime.RuntimePersistentEntity<Object>) assoc.getAssociatedEntity());
                }
            }
        }

        boolean result = super.triggerPrePersist();
        if (LOG.isDebugEnabled()) {
            LOG.debug("triggerPrePersist: result={}", result);
        }
        return result;
    }

    @Override
    protected void triggerPostPersist() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("triggerPostPersist: entity={}", entity);
        }
        super.triggerPostPersist();
    }

    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        if ((operationType == OperationType.UPDATE || operationType == OperationType.DELETE) && persistentEntity.getVersion() != null) {
            preVersionValue = persistentEntity.getVersion().getProperty().get(entity);
        }
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
