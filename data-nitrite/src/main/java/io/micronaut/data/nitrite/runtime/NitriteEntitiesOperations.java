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
import java.util.IdentityHashMap;
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
    /** Prior version per entity, keyed by identity. Populated by triggerPre for !insert paths. */
    private IdentityHashMap<T, Object> priorVersions;

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

            NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(persistentEntity.getIntrospection().getBeanType());

            // Partition once: new entities have no ID, existing entities have one.
            List<T> newEntities = new ArrayList<>();
            List<T> existingEntities = new ArrayList<>();
            for (T entity : entities) {
                if (meta.idAccessor() != null && meta.idAccessor().get(entity) != null) {
                    existingEntities.add(entity);
                } else {
                    newEntities.add(entity);
                }
            }

            // Pre-phase: new entities (persist lifecycle).
            if (!newEntities.isEmpty()) {
                this.entities = newEntities;
                if (!triggerPrePersist() && persistentEntity.cascadesPersist()) {
                    cascadePre(Relation.Cascade.PERSIST);
                }
            }

            // Pre-phase: existing entities (update lifecycle).
            if (!existingEntities.isEmpty()) {
                this.entities = existingEntities;
                if (!triggerPreUpdate() && persistentEntity.cascadesUpdate()) {
                    cascadePre(Relation.Cascade.UPDATE);
                }
            }

            // Execute over the combined list. newEntities first so execute()'s index loop
            // stays stable; subList views below reflect any entity replacements made by execute().
            List<T> combined = new ArrayList<>(newEntities.size() + existingEntities.size());
            combined.addAll(newEntities);
            combined.addAll(existingEntities);
            this.entities = combined;
            execute();

            // Post-phase using subList views — mutations from execute() are visible here.
            int newCount = newEntities.size();
            if (newCount > 0) {
                this.entities = combined.subList(0, newCount);
                triggerPostPersist();
                if (persistentEntity.cascadesPersist()) {
                    cascadePost(Relation.Cascade.PERSIST);
                }
            }
            if (newCount < combined.size()) {
                this.entities = combined.subList(newCount, combined.size());
                triggerPostUpdate();
                if (persistentEntity.cascadesUpdate()) {
                    cascadePost(Relation.Cascade.UPDATE);
                }
            }
            this.entities = combined;

        } catch (OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            failed(e, "PERSIST");
        }
    }

    /**
     * Delete all entities with optimistic locking support.
     */
    @SuppressWarnings("unchecked")
    public void delete() {
        if (entities.isEmpty()) {
            return;
        }

        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

        List<Filter> filters = new ArrayList<>();
        List<T> entitiesToDelete = new ArrayList<>();

        for (T entity : entities) {
            Object idValue = entityMapper.getEntityIdValue(entity, persistentEntity.getIntrospection().getBeanType());
            if (idValue == null) {
                continue;
            }

            Filter filter = entityMapper.idEqualsFilter(persistentEntity.getIntrospection().getBeanType(), idValue);

            if (meta.versionProp() != null) {
                BeanProperty<T, Object> versionProperty = meta.versionProp().getProperty();
                Object versionValue = versionProperty.get(entity);
                filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(meta.versionProp().getPersistedName()).eq(helper.toFilterValue(versionValue)));
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

        int count = 0;
        for (Filter filter : filters) {
            if (collection.remove(filter, false).getAffectedCount() > 0) {
                count++;
            }
        }

        if (meta.versionProp() != null && count != entitiesToDelete.size()) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + entitiesToDelete.size() + " got: " + count);
        }

        for (T entity : entitiesToDelete) {
            entityEventListener.postRemove((EntityEventContext<Object>) new DefaultEntityEventContext<>(persistentEntity, entity));
        }

        entities = entitiesToDelete;
    }

    @Override
    protected void execute() throws RuntimeException {
        LOG.debug("execute: insert={}, entities count={}", insert, entities.size());

        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

        if (insert) {
            // saveAll() operation uses upsert semantics for each entity:
            // - If entity has no ID: generate ID and insert as new document
            // - If entity has ID: update (replace) existing document, or insert if not found
            List<Document> docsToInsert = new ArrayList<>();

            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);

                if (ctx.persisted.contains(entity)) {
                    continue;
                }

                Object id = meta.idAccessor() != null ? meta.idAccessor().get(entity) : null;
                if (id != null) {
                    if (meta.versionProp() != null && repositoryWriter.needsVersionInit(entity)) {
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
                    helper.generateIdIfNecessary(entity, type);
                    if (meta.versionProp() != null && repositoryWriter.needsVersionInit(entity)) {
                        BeanProperty<T, Object> versionProperty = meta.versionProp().getProperty();
                        entity = helper.updateEntityId(versionProperty, entity, 0L);
                        entities.set(i, entity);
                    }
                    docsToInsert.add(repositoryWriter.toDocument(entity));
                }
            }

            if (!docsToInsert.isEmpty()) {
                helper.logInsert(collection.getName(), "batch of " + docsToInsert.size());
                collection.insert(docsToInsert.toArray(new Document[0]));
                ctx.persisted.addAll(entities);
            }
        } else {
            // updateAll() operation: replace existing documents by ID.
            int expectedCount = entities.size();
            long affectedCount = 0;

            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);
                Object id = meta.idAccessor() != null ? meta.idAccessor().get(entity) : null;
                Filter filter = entityMapper.idEqualsFilter(meta, id);
                if (meta.versionProp() != null) {
                    Object versionValue = priorVersions != null ? priorVersions.get(entity) : null;
                    if (versionValue == null) {
                        versionValue = meta.versionProp().getProperty().get(entity);
                    }
                    filter = Filter.and(filter, org.dizitart.no2.filters.FluentFilter.where(meta.versionProp().getPersistedName()).eq(helper.toFilterValue(versionValue)));
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

            if (meta.versionProp() != null && affectedCount != expectedCount) {
                throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + expectedCount + " got: " + affectedCount);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        priorVersions = new IdentityHashMap<>();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(persistentEntity.getIntrospection().getBeanType());
        boolean vetoed = false;
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            // Capture pre-version before the event listener can modify the field.
            if (!insert && meta.versionProp() != null) {
                priorVersions.put(entity, meta.versionProp().getProperty().get(entity));
            }
            DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
            if (!fn.apply((EntityEventContext<Object>) event)) {
                vetoed = true;
                continue;
            }
            T newEntity = event.getEntity();
            if (entity != newEntity) {
                entities.set(i, newEntity);
                // Carry the captured version to the replacement instance.
                Object v = priorVersions.remove(entity);
                if (v != null) {
                    priorVersions.put(newEntity, v);
                }
            }
        }
        return vetoed;
    }

    @SuppressWarnings("unchecked")
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
