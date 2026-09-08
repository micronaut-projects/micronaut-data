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
package io.micronaut.data.nitrite.runtime.write;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.EntityExistsException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMeta;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterUtils;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.data.runtime.operations.internal.SyncEntitiesOperations;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.exceptions.UniqueConstraintException;
import org.dizitart.no2.filters.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Internal entities operations for Nitrite with automatic event firing and version handling.
 * <p>
 * <b>Save All (INSERT) Operation:</b> follows Jakarta Data {@code BasicRepository.save} semantics
 * for each entity: an identity found in the store is updated, and an identity not found in the
 * store is inserted.
 * This allows {@code saveAll()} to work for mixed batches of new and existing entities without
 * using Nitrite's insert-if-absent update option.
 * <p>
 * <b>Update All (UPDATE) Operation:</b> Replaces existing documents by ID, and never inserts.
 * An entity whose document is absent, or whose version does not match, affects no rows and raises
 * {@link OptimisticLockException}, versioned or not. An entity with no ID is skipped.
 *
 * @param <T> The entity type
 * @since 5.2.0
 */
@Internal
public final class NitriteEntitiesOperations<T> extends SyncEntitiesOperations<T, RuntimeException> {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteEntitiesOperations.class);

    private final NitriteOperationContext ctx;
    private final NitriteCollection collection;
    private List<T> entities;
    private final boolean insert;
    /** Whether the current execute call is writing the insert half of a mixed save batch. */
    private boolean executingInsert;
    private final NitriteEntityMapper entityMapper;
    private final ObjectRepositoryWriter<T> repositoryWriter;
    private final SyncCascadeOperations<NitriteOperationContext> cascadeOperations;
    private final NitriteOperationsHelper helper;
    /** Prior version per entity, keyed by identity. Populated before every lifecycle phase. */
    private @Nullable IdentityHashMap<T, Object> priorVersions;
    /** Documents actually removed, which can be fewer than the entities passed in. */
    private long affectedCount;

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
        this.executingInsert = insert;
    }

    @Override
    public List<T> getEntities() {
        return entities;
    }

    /**
     * The number of documents actually removed by {@link #delete()}, which is lower than the number
     * of entities passed in when an entity was vetoed, had no identity, or was already gone.
     *
     * @return the affected document count
     */
    public long getAffectedCount() {
        return affectedCount;
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

            // Jakarta Data save determines insert/update from whether the identity exists in the
            // store. A non-null assigned identity is not enough to select update. Explicit insert
            // is strict and therefore bypasses the existence check entirely.
            List<T> newEntities = new ArrayList<>();
            List<T> existingEntities = new ArrayList<>();
            if (insert || ctx.isSave()) {
                for (T entity : entities) {
                    Object id = entityMapper.getEntityIdValue(meta, entity);
                    boolean existing = false;
                    if (!ctx.isStrictInsert() && id != null) {
                        Filter identityFilter = entityMapper.idEqualsFilter(meta, id);
                        Filter legacyIdentityFilter = entityMapper.identityFieldEqualsFilter(meta, id);
                        existing = NitriteCrudOperations.exists(collection, identityFilter, legacyIdentityFilter);
                    }
                    if (existing) {
                        existingEntities.add(entity);
                    } else {
                        newEntities.add(entity);
                    }
                }
            } else {
                existingEntities.addAll(entities);
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

            // Execute each lifecycle half using its matching write operation. The collection
            // insert must not be attempted for entities that were classified as existing.
            if (!newEntities.isEmpty()) {
                this.entities = newEntities;
                executingInsert = true;
                execute();
            }
            if (!existingEntities.isEmpty()) {
                this.entities = existingEntities;
                executingInsert = false;
                execute();
            }

            // Post-phase over the halves themselves. An immutable entity given a generated id or
            // an initial version is replaced by a new instance in the list execute() was handed,
            // so the combined view has to be built from the halves after execute() has run.
            if (!newEntities.isEmpty()) {
                this.entities = newEntities;
                triggerPostPersist();
                if (persistentEntity.cascadesPersist()) {
                    cascadePost(Relation.Cascade.PERSIST);
                }
            }
            if (!existingEntities.isEmpty()) {
                this.entities = existingEntities;
                triggerPostUpdate();
                if (persistentEntity.cascadesUpdate()) {
                    cascadePost(Relation.Cascade.UPDATE);
                }
            }
            List<T> combined = new ArrayList<>(newEntities.size() + existingEntities.size());
            combined.addAll(newEntities);
            combined.addAll(existingEntities);
            this.entities = combined;

        } catch (EntityExistsException | OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            failed(e, "PERSIST");
        }
    }

    /**
     * Delete all entities with optimistic locking support.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void delete() {
        if (entities.isEmpty()) {
            return;
        }

        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

        List<Filter> filters = new ArrayList<>();
        List<Filter> legacyFilters = new ArrayList<>();
        List<T> entitiesToDelete = new ArrayList<>();

        for (T entity : entities) {
            Object idValue = entityMapper.getEntityIdValue(meta, entity);
            if (idValue == null) {
                continue;
            }

            Filter filter = entityMapper.idEqualsFilter(persistentEntity.getIntrospection().getBeanType(), idValue);

            if (meta.versionProp() != null) {
                BeanProperty<T, Object> versionProperty = meta.versionProp().getProperty();
                Object versionValue = versionProperty.get(entity);
                filter = Filter.and(filter, NitriteFilterUtils.eq(meta.versionProp().getPersistedName(), helper.toFilterValue(versionValue)));
            }
            Filter legacyFilter = legacyIdentityFilter(meta, idValue,
                meta.versionProp() == null ? null : meta.versionProp().getProperty().get(entity));

            DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
            if (entityEventListener.preRemove((EntityEventContext<Object>) event)) {
                entitiesToDelete.add(event.getEntity());
                filters.add(filter);
                legacyFilters.add(legacyFilter);
            }
        }

        if (entitiesToDelete.isEmpty()) {
            entities.clear();
            return;
        }

        int count = 0;
        for (int i = 0; i < filters.size(); i++) {
            if (NitriteCrudOperations.remove(collection, filters.get(i), legacyFilters.get(i)) > 0) {
                count++;
            }
        }
        affectedCount = count;

        if (count != entitiesToDelete.size()) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + entitiesToDelete.size() + " got: " + count);
        }

        for (T entity : entitiesToDelete) {
            entityEventListener.postRemove((EntityEventContext<Object>) new DefaultEntityEventContext<>(persistentEntity, entity));
        }

        entities = entitiesToDelete;
    }

    @Override
    protected void execute() throws RuntimeException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("execute: insert={}, entities count={}", executingInsert, entities.size());
        }

        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

        if (executingInsert) {
            // Standard save/insert operations use a strict batch insert. Existing save entities
            // were partitioned into the update lifecycle above; no insert-if-absent update is used.
            List<Document> docsToInsert = new ArrayList<>();

            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);

                if (ctx.persisted.contains(entity)) {
                    continue;
                }

                if (entityMapper.getEntityIdValue(meta, entity) == null) {
                    helper.generateIdIfNecessary(entity, type);
                }
                if (meta.versionProp() != null && repositoryWriter.needsVersionInit(entity)) {
                    BeanProperty<T, Object> versionProperty = meta.versionProp().getProperty();
                    entity = helper.updateEntityId(versionProperty, entity, 0L);
                    entities.set(i, entity);
                }
                Document doc = repositoryWriter.toDocument(entity);
                if (doc != null) {
                    docsToInsert.add(doc);
                }
            }

            if (!docsToInsert.isEmpty()) {
                if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                    helper.logInsert(collection.getName(), "batch of " + docsToInsert.size());
                }
                try {
                    collection.insert(docsToInsert.toArray(new Document[0]));
                } catch (UniqueConstraintException e) {
                    // Nitrite reports the collision without naming the document, and the batch is
                    // written as one call, so the identity cannot be narrowed down here.
                    throw new EntityExistsException(
                        "One or more entities already exist in collection: " + collection.getName(), e);
                }
                affectedCount = docsToInsert.size();
                ctx.persisted.addAll(entities);
            }
        } else {
            // updateAll() operation: replace existing documents by ID.
            int expectedCount = 0;
            long updatedCount = 0;

            for (int i = 0; i < entities.size(); i++) {
                T entity = entities.get(i);
                Object id = entityMapper.getEntityIdValue(meta, entity);
                if (id == null) {
                    continue;
                }
                expectedCount++;
                Filter filter = entityMapper.idEqualsFilter(meta, id);
                Object versionValue = null;
                if (meta.versionProp() != null) {
                    versionValue = priorVersions != null ? priorVersions.get(entity) : null;
                    if (versionValue == null) {
                        versionValue = meta.versionProp().getProperty().get(entity);
                    }
                    filter = Filter.and(filter, NitriteFilterUtils.eq(meta.versionProp().getPersistedName(), helper.toFilterValue(versionValue)));
                    long nextVersion = (versionValue == null ? 0L : ((Number) versionValue).longValue()) + 1;
                    entity = helper.updateEntityId(meta.versionProp().getProperty(), entity, nextVersion);
                    entities.set(i, entity);
                }
                Document update = repositoryWriter.toDocument(entity);
                if (update != null) {
                    helper.logUpdate(collection.getName(), filter, update);
                    long rows = NitriteCrudOperations.update(
                        collection, filter, legacyIdentityFilter(meta, id, versionValue), update);
                    updatedCount += rows;
                }
            }

            affectedCount = updatedCount;
            if (updatedCount != expectedCount) {
                throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + expectedCount + " got: " + updatedCount);
            }
        }
    }

    /**
     * The filter over the identity field for a document whose Nitrite key is not its identity.
     * A versioned filter is an AND over the identity, so the fallback has to carry the version
     * clause too or it would write over a stale document.
     *
     * @param meta the entity metadata
     * @param id the identity value
     * @param versionValue the version the document is expected to hold, or {@code null} when the
     *        entity is unversioned
     * @return the fallback filter, or {@code null} when the identity is always the document key
     */
    private @Nullable Filter legacyIdentityFilter(NitriteEntityMeta<T> meta, Object id, @Nullable Object versionValue) {
        Filter legacyIdentityFilter = entityMapper.identityFieldEqualsFilter(meta, id);
        if (legacyIdentityFilter == null || meta.versionProp() == null) {
            return legacyIdentityFilter;
        }
        return Filter.and(legacyIdentityFilter,
            NitriteFilterUtils.eq(meta.versionProp().getPersistedName(), helper.toFilterValue(versionValue)));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        priorVersions = new IdentityHashMap<>();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(persistentEntity.getIntrospection().getBeanType());
        IdentityHashMap<T, Boolean> vetoedSet = null;
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);
            // Capture pre-version before the event listener can modify the field.
            if (meta.versionProp() != null) {
                priorVersions.put(entity, meta.versionProp().getProperty().get(entity));
            }
            DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
            if (!fn.apply((EntityEventContext<Object>) event)) {
                priorVersions.remove(entity);
                if (vetoedSet == null) {
                    vetoedSet = new IdentityHashMap<>();
                }
                vetoedSet.put(entity, Boolean.TRUE);
                continue;
            }
            T newEntity = event.getEntity();
            if (!Objects.equals(entity, newEntity)) {
                entities.set(i, newEntity);
                // Carry the captured version to the replacement instance.
                Object v = priorVersions.remove(entity);
                if (v != null) {
                    priorVersions.put(newEntity, v);
                }
            }
        }
        if (vetoedSet != null) {
            veto(vetoedSet::containsKey);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void triggerPost(Consumer<EntityEventContext<Object>> fn) {
        IntStream.range(0, entities.size()).forEach(i -> {
            T entity = entities.get(i);
            DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
            fn.accept((EntityEventContext<Object>) event);
            T newEntity = event.getEntity();
            if (!Objects.equals(entity, newEntity)) {
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
