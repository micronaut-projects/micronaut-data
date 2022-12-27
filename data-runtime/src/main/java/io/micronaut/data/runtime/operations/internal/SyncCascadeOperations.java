/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

/**
 * Synchronous cascade operations.
 *
 * @param <Ctx> The operation context.
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public final class SyncCascadeOperations<Ctx extends OperationContext> extends AbstractCascadeOperations {

    private static final Logger LOG = LoggerFactory.getLogger(SyncCascadeOperations.class);
    private final SyncCascadeOperationsHelper<Ctx> helper;

    /**
     * Default constructor.
     *
     * @param conversionService The conversionService
     * @param helper            The helper
     */
    public SyncCascadeOperations(ConversionService conversionService, SyncCascadeOperationsHelper<Ctx> helper) {
        super(conversionService);
        this.helper = helper;
    }

    /**
     * Cascade the entity operation.
     *
     * @param ctx              The context
     * @param entity           The entity instance
     * @param persistentEntity The persistent entity
     * @param isPost           Is post cascade?
     * @param cascadeType      The cascade type
     * @param <T>              The entity type
     * @return The entity instance
     */
    public <T> T cascadeEntity(Ctx ctx,
                               T entity,
                               RuntimePersistentEntity<T> persistentEntity,
                               boolean isPost,
                               Relation.Cascade cascadeType) {
        List<CascadeOp> cascadeOps = new ArrayList<>();
        cascade(ctx.annotationMetadata, ctx.repositoryType,
                isPost, cascadeType,
                CascadeContext.of(ctx.associations, entity, (RuntimePersistentEntity<Object>) persistentEntity),
                persistentEntity, entity, cascadeOps);
        for (CascadeOp cascadeOp : cascadeOps) {
            if (cascadeOp instanceof CascadeOneOp) {
                CascadeOneOp cascadeOneOp = (CascadeOneOp) cascadeOp;
                RuntimePersistentEntity<Object> childPersistentEntity = cascadeOp.childPersistentEntity;
                Object child = cascadeOneOp.child;
                if (ctx.persisted.contains(child)) {
                    continue;
                }
                RuntimePersistentProperty<Object> identity = childPersistentEntity.getIdentity();
                boolean hasId = identity.getProperty().get(child) != null;
                if ((!hasId || identity instanceof Association) && (cascadeType == Relation.Cascade.PERSIST)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Cascading PERSIST for '{}' association: '{}'", persistentEntity.getName(), cascadeOp.ctx.associations);
                    }
                    Object persisted = helper.persistOne(ctx, child, childPersistentEntity);
                    entity = afterCascadedOne(entity, cascadeOp.ctx.associations, child, persisted);
                    child = persisted;
                } else if (hasId && (cascadeType == Relation.Cascade.UPDATE)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Cascading MERGE for '{}' ({}) association: '{}'", persistentEntity.getName(),
                                persistentEntity.getIdentity().getProperty().get(entity), cascadeOp.ctx.associations);
                    }
                    Object updated = helper.updateOne(ctx, child, childPersistentEntity);
                    entity = afterCascadedOne(entity, cascadeOp.ctx.associations, child, updated);
                    child = updated;
                }
                RuntimeAssociation<Object> association = (RuntimeAssociation) cascadeOp.ctx.getAssociation();
                if (!hasId
                        && (cascadeType == Relation.Cascade.PERSIST || cascadeType == Relation.Cascade.UPDATE)
                        && SqlQueryBuilder.isForeignKeyWithJoinTable(association)) {

                    helper.persistManyAssociation(ctx, association, entity, (RuntimePersistentEntity<Object>) persistentEntity, child, childPersistentEntity);
                }
                ctx.persisted.add(child);
            } else if (cascadeOp instanceof CascadeManyOp) {
                CascadeManyOp cascadeManyOp = (CascadeManyOp) cascadeOp;
                RuntimePersistentEntity<Object> childPersistentEntity = cascadeManyOp.childPersistentEntity;

                List<Object> entities;
                if (cascadeType == Relation.Cascade.UPDATE) {
                    entities = CollectionUtils.iterableToList(cascadeManyOp.children);
                    for (ListIterator<Object> iterator = entities.listIterator(); iterator.hasNext(); ) {
                        Object child = iterator.next();
                        if (ctx.persisted.contains(child)) {
                            continue;
                        }
                        RuntimePersistentProperty<Object> identity = childPersistentEntity.getIdentity();
                        Object value;
                        if (identity.getProperty().get(child) == null) {
                            value = helper.persistOne(ctx, child, childPersistentEntity);
                        } else {
                            value = helper.updateOne(ctx, child, childPersistentEntity);
                        }
                        iterator.set(value);
                    }
                } else if (cascadeType == Relation.Cascade.PERSIST) {
                    if (helper.isSupportsBatchInsert(ctx, childPersistentEntity)) {
                        RuntimePersistentProperty<Object> identity = childPersistentEntity.getIdentity();
                        Predicate<Object> veto = val -> ctx.persisted.contains(val) || identity.getProperty().get(val) != null && !(identity instanceof Association);
                        entities = helper.persistBatch(ctx, cascadeManyOp.children, childPersistentEntity, veto);
                    } else {
                        entities = CollectionUtils.iterableToList(cascadeManyOp.children);
                        for (ListIterator<Object> iterator = entities.listIterator(); iterator.hasNext(); ) {
                            Object child = iterator.next();
                            if (ctx.persisted.contains(child)) {
                                continue;
                            }
                            RuntimePersistentProperty<Object> identity = childPersistentEntity.getIdentity();
                            if (identity.getProperty().get(child) != null) {
                                continue;
                            }
                            Object persisted = helper.persistOne(ctx, child, childPersistentEntity);
                            iterator.set(persisted);
                        }
                    }
                } else {
                    continue;
                }

                entity = afterCascadedMany(entity, cascadeOp.ctx.associations, cascadeManyOp.children, entities);

                RuntimeAssociation<Object> association = (RuntimeAssociation) cascadeOp.ctx.getAssociation();
                if (SqlQueryBuilder.isForeignKeyWithJoinTable(association) && !entities.isEmpty()) {
                    if (helper.isSupportsBatchInsert(ctx, childPersistentEntity)) {
                        helper.persistManyAssociationBatch(ctx, association,
                                cascadeOp.ctx.parent, cascadeOp.ctx.parentPersistentEntity, entities, childPersistentEntity);
                    } else {
                        for (Object e : cascadeManyOp.children) {
                            if (ctx.persisted.contains(e)) {
                                continue;
                            }
                            helper.persistManyAssociation(ctx, association,
                                    cascadeOp.ctx.parent, cascadeOp.ctx.parentPersistentEntity, e, childPersistentEntity);
                        }
                    }
                }
                ctx.persisted.addAll(entities);
            }
        }
        return entity;
    }

    /**
     * The cascade operations helper.
     *
     * @param <Ctx> The operation context.
     */
    public interface SyncCascadeOperationsHelper<Ctx extends OperationContext> {

        /**
         * Is supports batch insert.
         *
         * @param ctx              The context
         * @param persistentEntity The persistent entity
         * @return True if supports
         */
        default boolean isSupportsBatchInsert(Ctx ctx, RuntimePersistentEntity<?> persistentEntity) {
            return true;
        }

        /**
         * Is supports batch update.
         *
         * @param ctx              The context
         * @param persistentEntity The persistent entity
         * @return True if supports
         */
        default boolean isSupportsBatchUpdate(Ctx ctx, RuntimePersistentEntity<?> persistentEntity) {
            return true;
        }

        /**
         * Is supports batch delete.
         *
         * @param ctx              The context
         * @param persistentEntity The persistent entity
         * @return True if supports
         */
        default boolean isSupportsBatchDelete(Ctx ctx, RuntimePersistentEntity<?> persistentEntity) {
            return true;
        }

        /**
         * Persist one entity during cascade.
         *
         * @param ctx              The context
         * @param entityValue      The entity value
         * @param persistentEntity The persistent entity
         * @param <T>              The entity type
         * @return The entity value
         */
        <T> T persistOne(Ctx ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity);

        /**
         * Persist multiple entities in batch during cascade.
         *
         * @param ctx              The context
         * @param entityValues     The entity values
         * @param persistentEntity The persistent entity
         * @param predicate        The veto predicate
         * @param <T>              The entity type
         * @return The entity values
         */
        <T> List<T> persistBatch(Ctx ctx,
                                 Iterable<T> entityValues,
                                 RuntimePersistentEntity<T> persistentEntity,
                                 Predicate<T> predicate);

        /**
         * Update one entity during cascade.
         *
         * @param ctx              The context
         * @param entityValue      The entity value
         * @param persistentEntity The persistent entity
         * @param <T>              The entity type
         * @return The entity value
         */
        <T> T updateOne(Ctx ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity);

        /**
         * Persist JOIN table relationship.
         *
         * @param ctx                    The context
         * @param runtimeAssociation     The association
         * @param parentEntityValue      The parent entity value
         * @param parentPersistentEntity The parent persistent entity
         * @param childEntityValue       The child entity value
         * @param childPersistentEntity  The child persistent entity
         */
        void persistManyAssociation(Ctx ctx,
                                    RuntimeAssociation runtimeAssociation,
                                    Object parentEntityValue, RuntimePersistentEntity<Object> parentPersistentEntity,
                                    Object childEntityValue, RuntimePersistentEntity<Object> childPersistentEntity);

        /**
         * Persist JOIN table relationships in batch.
         *
         * @param ctx                    The context
         * @param runtimeAssociation     The association
         * @param parentEntityValue      The parent entity value
         * @param parentPersistentEntity The parent persistent entity
         * @param childEntityValues      The child entity values
         * @param childPersistentEntity  The child persistent entity
         */
        void persistManyAssociationBatch(Ctx ctx,
                                         RuntimeAssociation runtimeAssociation,
                                         Object parentEntityValue, RuntimePersistentEntity<Object> parentPersistentEntity,
                                         Iterable<Object> childEntityValues, RuntimePersistentEntity<Object> childPersistentEntity);
    }


}
