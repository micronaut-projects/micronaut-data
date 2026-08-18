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
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMeta;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterUtils;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntityOperations;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations.SyncCascadeOperationsHelper;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.filters.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
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
 * @since 5.2.0
 */
@Internal
public final class NitriteEntityOperations<T> extends AbstractSyncEntityOperations<NitriteOperationContext, T, RuntimeException> {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteEntityOperations.class);
    private final NitriteCollection collection;

    private final ObjectRepositoryWriter<T> repositoryWriter;
    private final NitriteEntityMapper entityMapper;
    private final NitriteOperationsHelper helper;
    private final OperationType operationType;
    private @Nullable Object preVersionValue;
    private long affectedCount;

    /**
     * Supported entity operation types.
     */
    public enum OperationType {
        /** Represents a new entity insertion operation. */
        INSERT,
        /** Represents an existing entity update operation. */
        UPDATE,
        /** Represents an entity deletion operation. */
        DELETE
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
    public void delete() {
        super.delete();
    }

    /**
     * The number of documents the executed operation actually affected. A vetoed operation, or an
     * update or delete of an entity without an identity, affects nothing.
     *
     * @return the affected document count
     */
    public long getAffectedCount() {
        return affectedCount;
    }

    /**
     * Override persist to handle upsert semantics correctly.
     * If the entity has an existing ID, trigger update lifecycle events (preUpdate/postUpdate)
     * to ensure version increments and @DateUpdated fields are handled correctly.
     */
    @Override
    public void persist() {
        try {
            collectAutoPopulatedPreviousValues();

            // Cache NitriteEntityMeta at method start - avoids repeated registry lookups
            Class<T> type = persistentEntity.getIntrospection().getBeanType();
            NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

            // A non-null id alone isn't proof the row already exists: entities with a
            // manually-assigned id (no @GeneratedValue - UUID, String, Long, ...) always
            // carry a non-null id, even on their very first save. Only treat the id as
            // evidence of an existing row when it's framework-generated (null pre-insert),
            // or otherwise confirm by checking the collection.
            Object idValue = meta.idAccessor() != null ? meta.idAccessor().get(entity) : null;
            boolean isUpdate;
            if (ctx.isStrictInsert() || idValue == null) {
                isUpdate = false;
            } else if (meta.idProp() != null && meta.idProp().isGenerated()) {
                isUpdate = true;
            } else {
                Filter existsFilter = entityMapper.idEqualsFilter(meta, idValue);
                isUpdate = !collection.find(existsFilter).isEmpty();
            }

            if (isUpdate) {
                // Entity has ID - use update lifecycle for proper version/@DateUpdated handling
                boolean vetoed = triggerPreUpdate();
                if (vetoed) {
                    return;
                }
                boolean cascades = persistentEntity.cascadesUpdate();
                if (cascades) {
                    cascadePre(Relation.Cascade.UPDATE);
                }
                execute(meta);
                triggerPostUpdate();
                if (cascades) {
                    cascadePost(Relation.Cascade.UPDATE);
                }
            } else {
                // No ID - use standard persist lifecycle
                boolean vetoed = triggerPrePersist();
                if (vetoed) {
                    return;
                }
                boolean cascades = persistentEntity.cascadesPersist();
                if (cascades) {
                    cascadePre(Relation.Cascade.PERSIST);
                }
                execute(meta);
                triggerPostPersist();
                if (cascades) {
                    cascadePost(Relation.Cascade.PERSIST);
                }
            }
        } catch (EntityExistsException e) {
            throw e;
        } catch (Exception e) {
            failed(e, "PERSIST");
        }
    }

    @Override
    protected void execute() throws RuntimeException {
        // Cache NitriteEntityMeta at method start - avoids repeated registry lookups
        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);
        execute(meta);
    }

    /**
     * Execute with pre-computed metadata.
     * @param meta the pre-computed entity metadata
     */
    private void execute(NitriteEntityMeta<T> meta) throws RuntimeException {
        LOG.debug("execute: operationType={}, entity={}", operationType, entity);
        // Skip if already persisted in this context
        if (operationType == OperationType.INSERT && ctx.persisted.contains(entity)) {
            LOG.debug("execute: skipping INSERT because already persisted");
            return;
        }

        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        if (operationType == OperationType.INSERT) {
            // Save operation uses upsert semantics:
            // - If entity has no ID: generate ID and insert as new document
            // - If entity has ID: update (replace) existing document, or insert if not found
            // This allows save() to work for both create and update scenarios
            // Use cached idAccessor from meta - eliminates chained lookups
            Object entityId = meta.idAccessor() != null ? meta.idAccessor().get(entity) : null;

            if (entityId != null) {
                if (ctx.isStrictInsert()) {
                    Filter filter = entityMapper.idEqualsFilter(meta, entityId);
                    if (!collection.find(filter).isEmpty()) {
                        throw new EntityExistsException("Entity already exists with id: " + entityId);
                    }
                    RuntimePersistentProperty<T> versionProp = meta.versionProp();
                    if (versionProp != null && repositoryWriter.needsVersionInit(entity)) {
                        BeanProperty<T, Object> versionProperty = versionProp.getProperty();
                        entity = helper.updateEntityId(versionProperty, entity, 0L);
                    }
                    Document doc = repositoryWriter.toDocument(entity);
                    if (doc != null) {
                        helper.logInsert(collection.getName(), doc);
                        collection.insert(doc);
                    }
                    ctx.persisted.add(entity);
                    return;
                }
                // Entity has ID - use upsert (update with insert-if-absent)
                // Initialize version to 0 if not set (for optimistic locking)
                RuntimePersistentProperty<T> versionProp = meta.versionProp();
                if (versionProp != null && repositoryWriter.needsVersionInit(entity)) {
                    BeanProperty<T, Object> versionProperty = versionProp.getProperty();
                    entity = helper.updateEntityId(versionProperty, entity, 0L);
                }
                Document doc = repositoryWriter.toDocument(entity);
                if (doc != null) {
                    Filter filter = entityMapper.idEqualsFilter(meta, entityId);
                    helper.logUpdate(collection.getName(), filter, doc);
                    long rows = collection.update(filter, doc, UpdateOptions.updateOptions(true)).getAffectedCount();
                    if (meta.versionProp() != null) {
                        checkOptimisticLocking(rows);
                    }
                }
            } else {
                // No ID - generate and insert as new
                helper.generateIdIfNecessary(entity, type);
                // Initialize version to 0 if not set (for optimistic locking)
                RuntimePersistentProperty<T> versionProp = meta.versionProp();
                if (versionProp != null && repositoryWriter.needsVersionInit(entity)) {
                    BeanProperty<T, Object> versionProperty = versionProp.getProperty();
                    entity = helper.updateEntityId(versionProperty, entity, 0L);
                }
                Document doc = repositoryWriter.toDocument(entity);
                if (doc != null) {
                    helper.logInsert(collection.getName(), doc);
                    collection.insert(doc);
                    affectedCount = 1;
                    Object generatedId = doc.get("_id");
                    if (generatedId != null && meta.idAccessor() != null && meta.idAccessor().get(entity) == null) {
                        entity = helper.updateEntityId(meta.idAccessor(), entity, generatedId);
                    }
                }
            }
            ctx.persisted.add(entity);
        } else if (operationType == OperationType.UPDATE) {
            // Update operation: replace existing document by ID
            // Requires entity to have an ID; throws OptimisticLockException if version mismatch
            // Note: VersionGeneratingEntityEventListener.preUpdate() already incremented the version
            // Use cached idAccessor from meta - eliminates chained lookups
            Object id = meta.idAccessor() != null ? meta.idAccessor().get(entity) : null;
            // A transient entity has nothing to update: an "id == null" filter combined with upsert
            // would insert it instead. Report zero affected rows rather than silently inserting.
            if (id == null) {
                affectedCount = 0;
                return;
            }
            persistNewCascadeChildren(meta);
            Filter filter = entityMapper.idEqualsFilter(meta, id);
            if (meta.versionProp() != null) {
                Object versionValue = preVersionValue;
                if (versionValue == null) {
                    versionValue = meta.versionProp().getProperty().get(entity);
                }
                filter = Filter.and(filter, NitriteFilterUtils.eq(meta.versionProp().getPersistedName(), helper.toFilterValue(versionValue)));
            }
            Document update = repositoryWriter.toDocument(entity);
            if (update != null) {
                helper.logUpdate(collection.getName(), filter, update);
                // An unversioned update runs with Nitrite's upsert option, so update() and
                // updateAll() of an entity whose id matches no document still write that document.
                // This is deliberate: Nitrite has no notion of a detached entity, so an id assigned
                // by the application is the only evidence of what the caller intends to store, and
                // re-attaching such an entity through update() must not lose it. It differs from
                // save() only in that save() also assigns a generated id and runs the insert
                // cascade for new children.
                // A versioned entity keeps strict semantics: the version is part of the filter, so a
                // row that is absent (or stale) affects nothing and raises an optimistic-lock
                // failure instead of being inserted. An entity with no id at all is rejected above,
                // before this point, rather than inserted.
                // Documented in src/main/docs/guide/nitrite/nitriteLimitations.adoc; covered by
                // NitriteUpdateUpsertSemanticsSpec.
                boolean upsert = meta.versionProp() == null;
                long rows = collection.update(filter, update, UpdateOptions.updateOptions(upsert)).getAffectedCount();
                affectedCount = rows;
                checkOptimisticLocking(rows);
            }
        } else {
            // Delete operation
            // Use cached idAccessor from meta - eliminates chained lookups
            Object id = meta.idAccessor() != null ? meta.idAccessor().get(entity) : null;
            if (id == null) {
                // Without an identity there is no document to remove; an "id == null" filter would
                // match every identity-less document in the collection.
                affectedCount = 0;
                return;
            }
            Filter filter = entityMapper.idEqualsFilter(meta, id);
            if (meta.versionProp() != null) {
                Object versionValue = preVersionValue;
                if (versionValue == null) {
                    versionValue = meta.versionProp().getProperty().get(entity);
                }
                filter = Filter.and(filter, NitriteFilterUtils.eq(meta.versionProp().getPersistedName(), helper.toFilterValue(versionValue)));
            }
            helper.logFind(collection.getName(), filter);
            long rows = collection.remove(filter, false).getAffectedCount();
            affectedCount = rows;
            checkOptimisticLocking(rows);
        }
    }

    @SuppressWarnings("unchecked")
    private void persistNewCascadeChildren(NitriteEntityMeta<T> meta) {
        for (RuntimeAssociation<T> assoc : meta.cascadeProps()) {
            RuntimePersistentEntity<Object> associatedEntity =
                (RuntimePersistentEntity<Object>) assoc.getAssociatedEntity();
            if (!associatedEntity.hasIdentity() || associatedEntity.hasCompositeIdentity()) {
                continue;
            }
            RuntimePersistentProperty<Object> associatedId = associatedEntity.getIdentity();
            BeanProperty<Object, Object> backRefProperty = null;
            String mappedBy = assoc.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
            if (mappedBy != null) {
                RuntimePersistentProperty<?> backProp = associatedEntity.getPropertyByName(mappedBy);
                if (backProp != null) {
                    backRefProperty = (BeanProperty<Object, Object>) backProp.getProperty();
                }
            }
            Object value = assoc.getProperty().get(entity);
            if (value instanceof Iterable<?> iterable) {
                for (Object child : iterable) {
                    if (child != null && backRefProperty != null && backRefProperty.get(child) == null) {
                        backRefProperty.set(child, entity);
                    }
                    if (child != null && associatedId.getProperty().get(child) == null) {
                        ((SyncCascadeOperationsHelper<NitriteOperationContext>) helper).persistOne(ctx, child, associatedEntity);
                    }
                }
            } else if (value != null) {
                if (backRefProperty != null && backRefProperty.get(value) == null) {
                    backRefProperty.set(value, entity);
                }
                if (associatedId.getProperty().get(value) == null) {
                    ((SyncCascadeOperationsHelper<NitriteOperationContext>) helper).persistOne(ctx, value, associatedEntity);
                }
            }
        }
    }

    private void checkOptimisticLocking(long received) {
        if (entityMapper.getOrBuildMeta(persistentEntity.getIntrospection().getBeanType()).versionProp() != null && received != 1) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + 1 + " got: " + received);
        }
    }

    @Override
    protected boolean triggerPrePersist() {
        LOG.debug("triggerPrePersist: entity={}", entity);

        // Generate ID early so children can reference it
        Class<T> type = persistentEntity.getIntrospection().getBeanType();
        helper.generateIdIfNecessary(entity, type);

        // Use pre-computed cascadeProps from metadata - avoids iterating all properties + instanceof checks
        NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);
        for (RuntimeAssociation<T> assoc : meta.cascadeProps()) {
            Object value = assoc.getProperty().get(entity);
            if (value instanceof Iterable<?> iterable) {
                // No ArrayList copy needed - persistBatch accepts Iterable directly
                if (iterable.iterator().hasNext()) {
                    @SuppressWarnings("unchecked")
                    Iterable<Object> iterableObjects = (Iterable<Object>) iterable;
                    ((SyncCascadeOperationsHelper<NitriteOperationContext>) helper).persistBatch(ctx, iterableObjects, (RuntimePersistentEntity<Object>) assoc.getAssociatedEntity(), x -> false);
                }
            } else if (value != null) {
                ((SyncCascadeOperationsHelper<NitriteOperationContext>) helper).persistOne(ctx, value, (RuntimePersistentEntity<Object>) assoc.getAssociatedEntity());
            }
        }

        boolean result = super.triggerPrePersist();
        LOG.debug("triggerPrePersist: result={}", result);
        return result;
    }

    @Override
    protected void triggerPostPersist() {
        LOG.debug("triggerPostPersist: entity={}", entity);
        super.triggerPostPersist();
    }

    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        NitriteEntityMeta<T> triggerMeta = entityMapper.getOrBuildMeta(persistentEntity.getIntrospection().getBeanType());
        if ((operationType == OperationType.UPDATE || operationType == OperationType.DELETE) && triggerMeta.versionProp() != null) {
            preVersionValue = triggerMeta.versionProp().getProperty().get(entity);
        }
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
        boolean vetoed = !fn.apply((EntityEventContext<Object>) event);
        if (vetoed) {
            return true;
        }
        T newEntity = event.getEntity();
        if (!Objects.equals(entity, newEntity)) {
            entity = newEntity;
        }
        return false;
    }

    @Override
    protected void triggerPost(Consumer<EntityEventContext<Object>> fn) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, entity);
        fn.accept((EntityEventContext<Object>) event);
        T newEntity = event.getEntity();
        if (!Objects.equals(entity, newEntity)) {
            entity = newEntity;
        }
    }
}
