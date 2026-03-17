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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.data.runtime.operations.internal.SyncEntitiesOperations;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.filters.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Internal entities operations for Nitrite with automatic event firing and version handling.
 * Uses CollectionWriter for batch entity-to-Document conversion.
 *
 * @param <T> The entity type
 * @since 4.14.0
 */
@Internal
public final class NitriteEntitiesOperations<T> extends SyncEntitiesOperations<T, RuntimeException> {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteEntitiesOperations.class);

    private final NitriteOperationContext ctx;
    private final NitriteCollection collection;
    private List<T> entities;
    private final boolean insert;
    private final NitriteEntityMapper entityMapper;
    private final ObjectRepositoryWriter<T> repositoryWriter;
    private final CollectionWriter<T> collectionWriter;
    private final SyncCascadeOperations<NitriteOperationContext> cascadeOperations;
    private final NitriteOperationsHelper helper;
    private final EntityEventListener<Object> entityEventListener;
    private List<Object> preVersionValues;

    /**
     * Creates a new NitriteEntitiesOperations.
     *
     * @param ctx the operation context
     * @param cascadeOperations the cascade operations
     * @param entityEventListener the entity event listener
     * @param persistentEntity the persistent entity
     * @param conversionService the conversion service
     * @param entityMapper the entity mapper
     * @param helper the operations helper
     * @param entities the entities to operate on
     * @param insert true if this is an insert operation
     */
    public NitriteEntitiesOperations(
            NitriteOperationContext ctx,
            SyncCascadeOperations<NitriteOperationContext> cascadeOperations,
            EntityEventListener<Object> entityEventListener,
            RuntimePersistentEntity<T> persistentEntity,
            ConversionService conversionService,
            NitriteEntityMapper entityMapper,
            NitriteOperationsHelper helper,
            Iterable<T> entities,
            boolean insert) {
        super(entityEventListener, persistentEntity, conversionService);
        this.ctx = ctx;
        this.cascadeOperations = cascadeOperations;
        this.entityEventListener = entityEventListener;
        this.entityMapper = entityMapper;
        this.helper = helper;
        this.collection = helper.getCollection(persistentEntity.getIntrospection().getBeanType());
        this.repositoryWriter = new ObjectRepositoryWriter<>(entityMapper, persistentEntity);
        this.collectionWriter = new CollectionWriter<>(repositoryWriter);
        if (entities instanceof List) {
            this.entities = (List<T>) entities;
        } else {
            this.entities = new ArrayList<>();
            for (T entity : entities) {
                this.entities.add(entity);
            }
        }
        this.insert = insert;
    }

    @Override
    public List<T> getEntities() {
        return entities;
    }

    @Override
    public void persist() {
        if (insert) {
            entities.removeIf(ctx.persisted::contains);
        }
        if (entities.isEmpty()) {
            return;
        }
        try {
            super.persist();
        } catch (DataAccessException e) {
            // Unwrap OptimisticLockException from DataAccessException
            if (e.getCause() instanceof OptimisticLockException ole) {
                throw ole;
            }
            throw e;
        }
    }

    /**
     * Delete all entities with optimistic locking support.
     */
    public void delete() {
        if (entities.isEmpty()) {
            return;
        }

        // First pass: capture pre-version values and trigger pre-remove events
        List<Filter> filters = new ArrayList<>();
        List<T> entitiesToDelete = new ArrayList<>();
        preVersionValues = new ArrayList<>();

        for (T entity : entities) {
            Object idValue = entityMapper.getEntityIdValue(entity, (Class<T>) persistentEntity.getIntrospection().getBeanType());
            if (idValue == null) {
                continue;
            }

            Filter filter = entityMapper.idEqualsFilter((Class<T>) persistentEntity.getIntrospection().getBeanType(), idValue);

            // Add version filter for optimistic locking
            if (persistentEntity.getVersion() != null) {
                BeanProperty<T, Object> versionProperty = (BeanProperty<T, Object>) persistentEntity.getVersion().getProperty();
                Object versionValue = versionProperty.get(entity);
                preVersionValues.add(versionValue);
                filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(persistentEntity.getVersion().getPersistedName()).eq(helper.toFilterValue(versionValue)));
            } else {
                preVersionValues.add(null);
            }

            DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
            if (entityEventListener.preRemove((EntityEventContext<Object>) event)) {
                entitiesToDelete.add(event.getEntity());
                filters.add(filter);
            }
        }

        if (entitiesToDelete.isEmpty()) {
            entities.clear();
            return;
        }

        // Execute all deletes
        int count = 0;
        for (Filter filter : filters) {
            if (collection.remove(filter, false).getAffectedCount() > 0) {
                count++;
            }
        }

        // Check optimistic locking - process all before throwing
        if (persistentEntity.getVersion() != null && count != entitiesToDelete.size()) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + entitiesToDelete.size() + " got: " + count);
        }

        // Post-remove events
        for (T entity : entitiesToDelete) {
            entityEventListener.postRemove((EntityEventContext<Object>) new DefaultEntityEventContext<>(persistentEntity, entity));
        }

        entities = entitiesToDelete;
    }

    @Override
    protected void execute() throws RuntimeException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("execute: insert={}, entities count={}", insert, entities.size());
        }

        if (insert) {
            Class<T> type = (Class<T>) persistentEntity.getIntrospection().getBeanType();
            List<Document> docs = new ArrayList<>();
            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);

                // If it's already in the context, skip
                if (ctx.persisted.contains(entity)) {
                    continue;
                }

                // If the entity already has an ID, check if it's already in the collection
                Object id = entityMapper.getEntityIdValue(entity, type);
                if (id != null) {
                    Filter idFilter = entityMapper.idEqualsFilter(type, id);
                    if (collection.find(idFilter).firstOrNull() != null) {
                        ctx.persisted.add(entity);
                        continue;
                    }
                }

                helper.generateIdIfNecessary(entity, type);
                // Initialize version to 0 if not set (for optimistic locking)
                if (repositoryWriter.needsVersionInit(entity)) {
                    BeanProperty<T, Object> versionProperty = (BeanProperty<T, Object>) persistentEntity.getVersion().getProperty();
                    entity = helper.updateEntityId(versionProperty, entity, 0L);
                    entities.set(i, entity);
                }
                docs.add(repositoryWriter.toDocument(entity));
            }
            if (!docs.isEmpty()) {
                helper.logInsert(collection.getName(), "batch of " + docs.size());
                collection.insert(docs.toArray(new Document[0]));
                for (T entity : entities) {
                    ctx.persisted.add(entity);
                }
            }
        } else {
            // Update batch logic - process all entities and report total failures
            Class<T> type = (Class<T>) persistentEntity.getIntrospection().getBeanType();
            int expectedCount = entities.size();
            int affectedCount = 0;
            
            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);
                Filter filter = entityMapper.idEqualsFilter(type, entityMapper.getEntityIdValue(entity, type));
                if (persistentEntity.getVersion() != null) {
                    Object versionValue = (preVersionValues != null && i < preVersionValues.size()) ? preVersionValues.get(i) : null;
                    if (versionValue == null) {
                        versionValue = persistentEntity.getVersion().getProperty().get(entity);
                    }
                    filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(persistentEntity.getVersion().getPersistedName()).eq(helper.toFilterValue(versionValue)));
                    // Increment version (preVersionValue has the OLD version, add 1)
                    long nextVersion = (versionValue == null ? 0L : ((Number) versionValue).longValue()) + 1;
                    entity = helper.updateEntityId((BeanProperty<T, Object>) persistentEntity.getVersion().getProperty(), entity, nextVersion);
                    entities.set(i, entity);
                }
                Document update = repositoryWriter.toDocument(entity);
                helper.logUpdate(collection.getName(), filter, update);
                long rows = collection.update(filter, update, org.dizitart.no2.collection.UpdateOptions.updateOptions(false)).getAffectedCount();
                affectedCount += rows;
            }
            
            // Check optimistic locking after processing all entities
            if (persistentEntity.getVersion() != null && affectedCount != expectedCount) {
                throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + expectedCount + " got: " + affectedCount);
            }
        }
    }

    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        boolean vetoed = false;
        preVersionValues = new ArrayList<>();
        // First pass: capture pre-version values BEFORE event listeners are triggered
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            if (!insert && persistentEntity.getVersion() != null) {
                preVersionValues.add(persistentEntity.getVersion().getProperty().get(entity));
            } else {
                preVersionValues.add(null);
            }
        }
        // Second pass: trigger event listeners
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
            if (!fn.apply((EntityEventContext<Object>) event)) {
                vetoed = true;
                continue;
            }
            T newEntity = event.getEntity();
            if (entity != newEntity) {
                entities.set(i, newEntity);
            }
        }
        return vetoed;
    }

    @Override
    protected void triggerPost(Consumer<EntityEventContext<Object>> fn) {
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
            fn.accept((EntityEventContext<Object>) event);
            T newEntity = event.getEntity();
            if (entity != newEntity) {
                entities.set(i, newEntity);
            }
        }
    }

    @Override
    public void veto(Predicate<T> predicate) {
        entities.removeIf(predicate);
    }

    @Override
    protected void cascadePre(Relation.Cascade cascadeType) {
        for (int i = 0; i < entities.size(); i++) {
            entities.set(i, cascadeOperations.cascadeEntity(ctx, entities.get(i), persistentEntity, false, cascadeType));
        }
    }

    @Override
    protected void cascadePost(Relation.Cascade cascadeType) {
        for (int i = 0; i < entities.size(); i++) {
            entities.set(i, cascadeOperations.cascadeEntity(ctx, entities.get(i), persistentEntity, true, cascadeType));
        }
    }

    @Override
    protected void collectAutoPopulatedPreviousValues() {
    }
}
