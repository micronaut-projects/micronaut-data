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
import org.dizitart.no2.exceptions.UniqueConstraintException;
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
 * <b>Save (INSERT) Operation:</b> follows Jakarta Data {@code @Save}, which defines the operation
 * by what the store holds rather than by the state of the identity field: an identity the store
 * already holds behaves as {@code @Update}, and one it does not behaves as {@code @Insert}. So:
 * <ul>
 *   <li>If the entity has no ID, it is inserted as a new document</li>
 *   <li>If the entity has an ID that exists, the existing document is updated</li>
 *   <li>If the entity has an ID that does not exist, it is inserted as a new document</li>
 * </ul>
 * A non-null identity is therefore never on its own taken as proof of a stored document. It never
 * uses Nitrite's insert-if-absent update option.
 * <p>
 * Jakarta Data also requires the instance an insert or update returns to carry every value written
 * to the database, including generated identities and incremented versions. An immutable entity
 * cannot be given those in place, so what is returned is the replacement instance built here, not
 * the argument.
 * <p>
 * <b>Update (UPDATE) Operation:</b> Replaces an existing document by ID, and never inserts.
 * Requires the entity to have an ID. A document that is absent, or whose version does not match,
 * affects no rows and raises {@link OptimisticLockException}. Jakarta Data {@code @Update} states
 * both cases as one, and conditions neither on the entity carrying a version, so neither does this.
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
    private OperationType operationType;
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
     * Micronaut's common save interceptor selects the update operation for a non-null generated
     * identity. Jakarta Data save is conditional, though: the entity is inserted when that
     * identity is not present in the store. Re-enter the save resolver here so the provider can
     * distinguish that case from an explicit {@code update} operation.
     */
    @Override
    public void update() {
        if (ctx.isSave()) {
            persist();
        } else {
            super.update();
        }
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
     * Resolve Jakarta Data save semantics before firing lifecycle events. A non-null identity is
     * not by itself proof that a row exists: both assigned identities and generated identities can
     * be supplied for a new entity.
     */
    @Override
    public void persist() {
        try {
            collectAutoPopulatedPreviousValues();

            // Cache NitriteEntityMeta at method start - avoids repeated registry lookups
            Class<T> type = persistentEntity.getIntrospection().getBeanType();
            NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);

            Object idValue = entityMapper.getEntityIdValue(meta, entity);
            boolean isUpdate = false;
            if (!ctx.isStrictInsert() && idValue != null) {
                Filter identityFilter = entityMapper.idEqualsFilter(meta, idValue);
                isUpdate = NitriteCrudOperations.exists(collection, identityFilter);
            }

            if (isUpdate) {
                // The common Micronaut save interceptor represents a save of an entity with a
                // generated identity as an UPDATE operation. For assigned identities, however,
                // it represents save as INSERT and the provider resolves existence here. Keep
                // the lifecycle decision and the write decision aligned before executing.
                operationType = OperationType.UPDATE;
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
                operationType = OperationType.INSERT;
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
            // Both Jakarta Data save of a new entity and explicit insert use a strict collection
            // insert. Save(existing) is routed through OperationType.UPDATE by persist() above.
            if (entityMapper.getEntityIdValue(meta, entity) == null) {
                helper.generateIdIfNecessary(entity, type);
            }
            RuntimePersistentProperty<T> versionProp = meta.versionProp();
            if (versionProp != null && repositoryWriter.needsVersionInit(entity)) {
                BeanProperty<T, Object> versionProperty = versionProp.getProperty();
                entity = helper.updateEntityId(versionProperty, entity, 0L);
            }
            Document doc = repositoryWriter.toDocument(entity);
            if (doc != null) {
                helper.logInsert(collection.getName(), doc);
                try {
                    collection.insert(doc);
                } catch (UniqueConstraintException e) {
                    // Raised by Nitrite's own document key for a Long identity, and by the unique
                    // index NitriteCollectionRegistry puts on "id" for every other scalar identity.
                    throw new EntityExistsException(
                        "Entity already exists with id: " + entityMapper.getEntityIdValue(meta, entity), e);
                }
                affectedCount = 1;
                Object generatedId = doc.get("_id");
                if (generatedId != null && meta.idAccessor() != null && meta.idAccessor().get(entity) == null) {
                    entity = helper.updateEntityId(meta.idAccessor(), entity, generatedId);
                }
            }
            ctx.persisted.add(entity);
        } else if (operationType == OperationType.UPDATE) {
            // Update operation: replace existing document by ID
            // Requires entity to have an ID; throws OptimisticLockException if version mismatch
            // Note: VersionGeneratingEntityEventListener.preUpdate() already incremented the version
            Object id = entityMapper.getEntityIdValue(meta, entity);
            // A transient entity has nothing to update: an "id == null" filter would match every
            // identity-less document in the collection. Report zero affected rows instead.
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
                // Explicit update is strict for every entity, including unversioned entities.
                long rows = NitriteCrudOperations.update(collection, filter, update);
                affectedCount = rows;
                checkOptimisticLocking(rows);
            }
        } else {
            // Delete operation
            Object id = entityMapper.getEntityIdValue(meta, entity);
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
            long rows = NitriteCrudOperations.remove(collection, filter);
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

    /**
     * A write that resolved to an existing document must affect exactly that document. An insert
     * is exempt: it is counted by the collection insert itself.
     *
     * @param received the number of documents the write affected
     */
    private void checkOptimisticLocking(long received) {
        if (operationType != OperationType.INSERT && received != 1) {
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
