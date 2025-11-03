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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Reactive cascade operations.
 *
 * @param <Ctx> The operation context.
 * @author Denis Stepanov
 * @since 3.3
 */
public final class ReactiveCascadeOperations<Ctx extends OperationContext> extends AbstractCascadeOperations {

    private static final Logger LOG = LoggerFactory.getLogger(ReactiveCascadeOperations.class);

    private final ReactiveCascadeOperationsHelper<Ctx> helper;

    /**
     * The default cosntructor.
     *
     * @param conversionService The conversion service
     * @param helper            The helper
     */
    public ReactiveCascadeOperations(ConversionService conversionService, ReactiveCascadeOperationsHelper<Ctx> helper) {
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
    public <T> Mono<T> cascadeEntity(Ctx ctx,
                                     T entity,
                                     RuntimePersistentEntity<T> persistentEntity,
                                     boolean isPost,
                                     Relation.Cascade cascadeType) {
        List<CascadeOp> cascadeOps = new ArrayList<>();

        cascade(ctx.annotationMetadata, ctx.repositoryType, isPost, cascadeType,
                CascadeContext.of(ctx.associations, entity, (RuntimePersistentEntity<Object>) persistentEntity), persistentEntity, entity, cascadeOps);

        Mono<T> monoEntity = Mono.just(entity);

        for (CascadeOp cascadeOp : cascadeOps) {
            if (cascadeOp instanceof CascadeOneOp cascadeOneOp) {
                Object child = cascadeOneOp.child;
                RuntimePersistentEntity<Object> childPersistentEntity = cascadeOneOp.childPersistentEntity;
                RuntimeAssociation<Object> association = (RuntimeAssociation) cascadeOp.ctx.getAssociation();

                if (ctx.persisted.contains(child)) {
                    continue;
                }

                monoEntity = monoEntity.flatMap(e -> {

                RuntimePersistentProperty<Object> identity = childPersistentEntity.getIdentity();
                boolean hasId = identity.getProperty().get(child) != null;
                Mono<T> thisEntity;
                Mono<Object> childMono;
                if ((!hasId || identity instanceof Association) && (cascadeType == Relation.Cascade.PERSIST)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Cascading one PERSIST for '{}' association: '{}'", persistentEntity.getName(), cascadeOp.ctx.associations);
                    }
                    Mono<Object> persisted = helper.persistOne(ctx, child, childPersistentEntity).cache();
                    thisEntity = persisted.map(persistedEntity -> afterCascadedOne(e, cascadeOp.ctx.associations, child, persistedEntity));
                    childMono = persisted;
                } else if (hasId && (cascadeType == Relation.Cascade.UPDATE)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Cascading one UPDATE for '{}' ({}) association: '{}'", persistentEntity.getName(),
                                persistentEntity.getIdentity().getProperty().get(entity), cascadeOp.ctx.associations);
                    }
                    Mono<Object> updated = helper.updateOne(ctx, child, childPersistentEntity).cache();
                    thisEntity = updated.map(updatedEntity -> afterCascadedOne(e, cascadeOp.ctx.associations, child, updatedEntity));
                    childMono = updated;
                } else {
                    childMono = Mono.just(child);
                    thisEntity = Mono.just(e);
                }

                if (!hasId
                        && (cascadeType == Relation.Cascade.PERSIST || cascadeType == Relation.Cascade.UPDATE)
                        && SqlQueryBuilder.isForeignKeyWithJoinTable(association)) {
                    return childMono.flatMap(c -> {
                        if (ctx.persisted.contains(c)) {
                            return Mono.just(e);
                        }
                        ctx.persisted.add(c);
                        return thisEntity.flatMap(e2 -> {
                            Mono<Void> op = helper.persistManyAssociation(ctx, association, e2, (RuntimePersistentEntity<Object>) persistentEntity, c, childPersistentEntity);
                            return op.thenReturn(e2);
                        });
                    });
                } else {
                    return childMono.flatMap(c -> {
                        ctx.persisted.add(c);
                        return thisEntity;
                    });
                }
                });

            } else if (cascadeOp instanceof CascadeManyOp cascadeManyOp) {
                RuntimePersistentEntity<Object> childPersistentEntity = cascadeManyOp.childPersistentEntity;

                if (cascadeType == Relation.Cascade.UPDATE) {
                    monoEntity = updateChildren(ctx, monoEntity, cascadeOp, cascadeManyOp, childPersistentEntity, e -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Cascading many UPDATE for '{}' association: '{}'", persistentEntity.getName(), cascadeOp.ctx.associations);
                        }
                        Flux<Object> childrenFlux = Flux.empty();
                        for (Object child : cascadeManyOp.children) {
                            if (ctx.persisted.contains(child)) {
                                continue;
                            }
                            Mono<Object> modifiedEntity;
                            if (childPersistentEntity.getIdentity().getProperty().get(child) == null) {
                                modifiedEntity = helper.persistOne(ctx, child, childPersistentEntity);
                            } else {
                                modifiedEntity = helper.updateOne(ctx, child, childPersistentEntity);
                            }
                            childrenFlux = childrenFlux.concatWith(modifiedEntity);
                        }
                        return childrenFlux.collectList();
                    });
                } else if (cascadeType == Relation.Cascade.PERSIST) {
                    if (helper.isSupportsBatchInsert(ctx, persistentEntity)) {
                        monoEntity = updateChildren(ctx, monoEntity, cascadeOp, cascadeManyOp, childPersistentEntity, e -> {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Cascading many PERSIST for '{}' association: '{}'", persistentEntity.getName(), cascadeOp.ctx.associations);
                            }
                            RuntimePersistentProperty<Object> identity = childPersistentEntity.getIdentity();
                            Predicate<Object> veto = val -> ctx.persisted.contains(val) || identity.getProperty().get(val) != null && !(identity instanceof Association);
                            Flux<Object> childrenFlux = helper.persistBatch(ctx, cascadeManyOp.children, childPersistentEntity, veto);
                            // Concat children inserted now with children that were persisted before
                            for (Object child : cascadeManyOp.children) {
                                if (veto.test(child)) {
                                    childrenFlux = childrenFlux.concatWith(Flux.just(child));
                                }
                            }
                            return childrenFlux.collectList();
                        });
                    } else {
                        monoEntity = updateChildren(ctx, monoEntity, cascadeOp, cascadeManyOp, childPersistentEntity, e -> {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Cascading many PERSIST for '{}' association: '{}'", persistentEntity.getName(), cascadeOp.ctx.associations);
                            }

                            Flux<Object> childrenFlux = Flux.empty();
                            for (Object child : cascadeManyOp.children) {
                                if (ctx.persisted.contains(child) || childPersistentEntity.getIdentity().getProperty().get(child) != null) {
                                    childrenFlux = childrenFlux.concatWith(Mono.just(child));
                                    continue;
                                }
                                Mono<Object> persisted = helper.persistOne(ctx, child, childPersistentEntity);
                                childrenFlux = childrenFlux.concatWith(persisted);
                            }
                            return childrenFlux.collectList();
                        });
                    }
                }
            }
        }
        return monoEntity;
    }

    private <T> Mono<T> updateChildren(Ctx ctx,
                                       Mono<T> monoEntity,
                                       CascadeOp cascadeOp,
                                       CascadeManyOp cascadeManyOp,
                                       RuntimePersistentEntity<Object> childPersistentEntity,
                                       Function<T, Mono<List<Object>>> fn) {
        monoEntity = monoEntity.flatMap(e -> fn.apply(e).flatMap(newChildren -> {
            T entityAfterCascade = afterCascadedMany(e, cascadeOp.ctx.associations, cascadeManyOp.children, newChildren);
            RuntimeAssociation<Object> association = (RuntimeAssociation) cascadeOp.ctx.getAssociation();
            if (SqlQueryBuilder.isForeignKeyWithJoinTable(association)) {
                if (helper.isSupportsBatchInsert(ctx, cascadeOp.ctx.parentPersistentEntity)) {
                    Predicate<Object> veto = ctx.persisted::contains;
                    Mono<Void> op = helper.persistManyAssociationBatch(ctx, association, cascadeOp.ctx.parent, cascadeOp.ctx.parentPersistentEntity, newChildren, childPersistentEntity, veto);
                    return op.thenReturn(entityAfterCascade);
                } else {
                    Mono<T> res = Mono.just(entityAfterCascade);
                    for (Object child : newChildren) {
                        if (ctx.persisted.contains(child)) {
                            continue;
                        }
                        Mono<Void> op = helper.persistManyAssociation(ctx, association, cascadeOp.ctx.parent, cascadeOp.ctx.parentPersistentEntity, child, childPersistentEntity);
                        res = res.flatMap(op::thenReturn);
                    }
                    return res;
                }
            }
            ctx.persisted.addAll(newChildren);
            return Mono.just(entityAfterCascade);
        }));
        return monoEntity;
    }

    /**
     * The cascade operations helper.
     *
     * @param <Ctx> The operation context.
     */
    public interface ReactiveCascadeOperationsHelper<Ctx extends OperationContext> {

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
        <T> Mono<T> persistOne(Ctx ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity);

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
        <T> Flux<T> persistBatch(Ctx ctx,
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
        <T> Mono<T> updateOne(Ctx ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity);

        /**
         * Persist JOIN table relationship.
         *
         * @param ctx                    The context
         * @param runtimeAssociation     The association
         * @param parentEntityValue      The parent entity value
         * @param parentPersistentEntity The parent persistent entity
         * @param childEntityValue       The child entity value
         * @param childPersistentEntity  The child persistent entity
         * @return The empty mono
         */
        Mono<Void> persistManyAssociation(Ctx ctx,
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
         * @param veto                   The veto predicate
         * @return The empty mono
         */
        Mono<Void> persistManyAssociationBatch(Ctx ctx,
                                               RuntimeAssociation runtimeAssociation,
                                               Object parentEntityValue, RuntimePersistentEntity<Object> parentPersistentEntity,
                                               Iterable<Object> childEntityValues, RuntimePersistentEntity<Object> childPersistentEntity,
                                               Predicate<Object> veto);
    }

}
