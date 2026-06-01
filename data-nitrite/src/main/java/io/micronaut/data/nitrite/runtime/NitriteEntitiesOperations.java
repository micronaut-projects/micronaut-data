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
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMeta;
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
import java.util.stream.IntStream;

/**
 * Internal entities operations for Nitrite with automatic event firing and version handling.
 * <p>
 * <b>Save All (INSERT) Operation:</b> Uses upsert semantics for each entity:
 * <ul>
 *   <li>If an entity has no ID: generates an ID and inserts as a new document</li>
 *   <li>If an entity has an existing ID: updates (replaces) the document if found, or inserts if not found</li>
 * </ul>
 * This allows {@code saveAll()} to work for mixed batches of new and existing entities.
 * <p>
 * <b>Update All (UPDATE) Operation:</b> Replaces existing documents by ID.
 * Requires all entities to have IDs; throws {@link OptimisticLockException} if version mismatch occurs.
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
            collectAutoPopulatedPreviousValues();

            // Cache NitriteEntityMeta at batch start - avoids repeated registry lookups
            Class<T> type = persistentEntity.getIntrospection().getBeanType();
            NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

            List<T> newEntities = new ArrayList<>();
            List<T> existingEntities = new ArrayList<>();
            for (T entity : entities) {
                // Use cached idAccessor from meta - eliminates chained lookups
                boolean hasExistingId = meta.idAccessor() != null && meta.idAccessor().get(entity) != null;
                if (hasExistingId) {
                    existingEntities.add(entity);
                } else {
                    newEntities.add(entity);
                }
            }

            // Handle new entities (persist lifecycle)
            if (!newEntities.isEmpty()) {
                List<T> originalEntities = this.entities;
                this.entities = newEntities;
                boolean vetoed = triggerPrePersist();
                if (!vetoed) {
                    if (persistentEntity.cascadesPersist()) {
                        cascadePre(Relation.Cascade.PERSIST);
                    }
                    // execute() handles both new and existing via its own internal branching,
                    // but we call it here for the new ones.
                    // Actually, we can just let execute() run once for all if we handle events correctly.
                }
                this.entities = originalEntities;
            }

            // Handle existing entities (update lifecycle)
            if (!existingEntities.isEmpty()) {
                List<T> originalEntities = this.entities;
                this.entities = existingEntities;
                boolean vetoed = triggerPreUpdate();
                if (!vetoed) {
                    if (persistentEntity.cascadesUpdate()) {
                        cascadePre(Relation.Cascade.UPDATE);
                    }
                }
                this.entities = originalEntities;
            }

            execute();

            // Handle post-events (simplified for batch)
            // Trigger postPersist for new, postUpdate for existing
            List<T> finalNewEntities = new ArrayList<>();
            List<T> finalExistingEntities = new ArrayList<>();
            for (T entity : entities) {
                // Use cached idAccessor from meta - eliminates chained lookups
                if (meta.idAccessor() != null) {
                    meta.idAccessor().get(entity);
                }
                // Note: new entities now HAVE IDs if they were generated
                // So we should have tracked them before.
                // For simplicity, fire appropriate events based on initial state
                if (newEntities.contains(entity)) {
                    finalNewEntities.add(entity);
                } else {
                    finalExistingEntities.add(entity);
                }
            }

            if (!finalNewEntities.isEmpty()) {
                List<T> originalEntities = this.entities;
                this.entities = finalNewEntities;
                triggerPostPersist();
                if (persistentEntity.cascadesPersist()) {
                    cascadePost(Relation.Cascade.PERSIST);
                }
                this.entities = originalEntities;
            }

            if (!finalExistingEntities.isEmpty()) {
                List<T> originalEntities = this.entities;
                this.entities = finalExistingEntities;
                triggerPostUpdate();
                if (persistentEntity.cascadesUpdate()) {
                    cascadePost(Relation.Cascade.UPDATE);
                }
                this.entities = originalEntities;
            }

        } catch (OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            failed(e, "PERSIST");
        }
    }

    /**
     * Delete all entities with optimistic locking support.
     */
    public void delete() {
        if (entities.isEmpty()) {
            return;
        }

        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

        // First pass: capture pre-version values and trigger pre-remove events
        List<Filter> filters = new ArrayList<>();
        List<T> entitiesToDelete = new ArrayList<>();
        preVersionValues = new ArrayList<>();

        for (T entity : entities) {
            Object idValue = entityMapper.getEntityIdValue(entity, persistentEntity.getIntrospection().getBeanType());
            if (idValue == null) {
                continue;
            }

            Filter filter = entityMapper.idEqualsFilter(persistentEntity.getIntrospection().getBeanType(), idValue);

            // Add version filter for optimistic locking
            if (meta.versionProp() != null) {
                BeanProperty<T, Object> versionProperty = meta.versionProp().getProperty();
                Object versionValue = versionProperty.get(entity);
                preVersionValues.add(versionValue);
                filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(meta.versionProp().getPersistedName()).eq(helper.toFilterValue(versionValue)));
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
        if (meta.versionProp() != null && count != entitiesToDelete.size()) {
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
        LOG.debug("execute: insert={}, entities count={}", insert, entities.size());

        // Cache NitriteEntityMeta at batch start - avoids repeated registry lookups
        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

        if (insert) {
            // saveAll() operation uses upsert semantics for each entity:
            // - If entity has no ID: generate ID and insert as new document
            // - If entity has ID: update (replace) existing document, or insert if not found
            // This allows saveAll() to work for mixed batches of new and existing entities
            List<Document> docsToInsert = new ArrayList<>();

            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);

                // If it's already in the context, skip
                if (ctx.persisted.contains(entity)) {
                    continue;
                }

                // If the entity already has an ID, use upsert
                // Use cached idAccessor from meta - eliminates chained lookups
                Object id = meta.idAccessor() != null ? meta.idAccessor().get(entity) : null;
                if (id != null) {
                    // Entity has ID - use upsert (update with insert-if-absent)
                    // Initialize version to 0 if not set (for optimistic locking)
                    if (repositoryWriter.needsVersionInit(entity)) {
                        BeanProperty<T, Object> versionProperty = meta.versionProp().getProperty();
                        entity = helper.updateEntityId(versionProperty, entity, 0L);
                        entities.set(i, entity);
                    }
                    Document doc = repositoryWriter.toDocument(entity);
                    Filter filter = entityMapper.idEqualsFilter(meta, id);
                    helper.logUpdate(collection.getName(), filter, doc);
                    long rows = collection.update(filter, doc, org.dizitart.no2.collection.UpdateOptions.updateOptions(true)).getAffectedCount();
                    if (meta.versionProp() != null) {
                        if (rows != 1) {
                            throw new OptimisticLockException("Upsert expected 1 row but got " + rows);
                        }
                    }
                    ctx.persisted.add(entity);
                } else {
                    // No ID - generate and collect for batch insert
                    helper.generateIdIfNecessary(entity, type);
                    // Initialize version to 0 if not set (for optimistic locking)
                    if (repositoryWriter.needsVersionInit(entity)) {
                        BeanProperty<T, Object> versionProperty = meta.versionProp().getProperty();
                        entity = helper.updateEntityId(versionProperty, entity, 0L);
                        entities.set(i, entity);
                    }
                    docsToInsert.add(repositoryWriter.toDocument(entity));
                }
            }

            // Insert all new entities without IDs in batch
            if (!docsToInsert.isEmpty()) {
                helper.logInsert(collection.getName(), "batch of " + docsToInsert.size());
                collection.insert(docsToInsert.toArray(new Document[0]));
                ctx.persisted.addAll(entities);
            }
        } else {
            // updateAll() operation: replace existing documents by ID
            // Requires all entities to have IDs; throws OptimisticLockException if version mismatch
            int expectedCount = entities.size();
            long affectedCount = 0;

            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);
                // Use cached idAccessor from meta - eliminates chained lookups
                Object id = meta.idAccessor() != null ? meta.idAccessor().get(entity) : null;
                Filter filter = entityMapper.idEqualsFilter(meta, id);
                if (meta.versionProp() != null) {
                    Object versionValue = (preVersionValues != null && i < preVersionValues.size()) ? preVersionValues.get(i) : null;
                    if (versionValue == null) {
                        versionValue = meta.versionProp().getProperty().get(entity);
                    }
                    filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(meta.versionProp().getPersistedName()).eq(helper.toFilterValue(versionValue)));
                    // Increment version (preVersionValue has the OLD version, add 1)
                    long nextVersion = (versionValue == null ? 0L : ((Number) versionValue).longValue()) + 1;
                    entity = helper.updateEntityId(meta.versionProp().getProperty(), entity, nextVersion);
                    entities.set(i, entity);
                }
                Document update = repositoryWriter.toDocument(entity);
                helper.logUpdate(collection.getName(), filter, update);
                boolean upsert = meta.versionProp() == null;
                long rows = collection.update(filter, update, org.dizitart.no2.collection.UpdateOptions.updateOptions(upsert)).getAffectedCount();
                affectedCount += rows;
            }

            // Check optimistic locking after processing all entities
            if (meta.versionProp() != null && affectedCount != expectedCount) {
                throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + expectedCount + " got: " + affectedCount);
            }
        }
    }

    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        boolean vetoed = false;
        preVersionValues = new ArrayList<>();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(persistentEntity.getIntrospection().getBeanType());
        // First pass: capture pre-version values BEFORE event listeners are triggered
        for (T entity : entities) {
            if (!insert && meta.versionProp() != null) {
                preVersionValues.add(meta.versionProp().getProperty().get(entity));
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
        IntStream.range(0, entities.size()).forEach(i -> {
            T entity = entities.get(i);
            DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
            fn.accept((EntityEventContext<Object>) event);
            T newEntity = event.getEntity();
            if (entity != newEntity) {
                entities.set(i, newEntity);
            }
        });
    }

    @Override
    public void veto(Predicate<T> predicate) {
        entities.removeIf(predicate);
    }

    @Override
    protected void cascadePre(Relation.Cascade cascadeType) {
        entities.replaceAll(entity -> cascadeOperations.cascadeEntity(ctx, entity, persistentEntity, false, cascadeType));
    }

    @Override
    protected void cascadePost(Relation.Cascade cascadeType) {
        entities.replaceAll(entity -> cascadeOperations.cascadeEntity(ctx, entity, persistentEntity, true, cascadeType));
    }

    @Override
    protected void collectAutoPopulatedPreviousValues() {
    }
}
