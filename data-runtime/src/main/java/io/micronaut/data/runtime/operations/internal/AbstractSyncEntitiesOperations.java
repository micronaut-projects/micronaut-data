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
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract synchronous entities operations.
 *
 * @param <Ctx> The operation context
 * @param <T>   The entity type
 * @param <Exc> The exception
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public abstract class AbstractSyncEntitiesOperations<Ctx extends OperationContext, T, Exc extends Exception> extends SyncEntitiesOperations<T, Exc> {

    protected final Ctx ctx;
    protected final SyncCascadeOperations<Ctx> cascadeOperations;
    protected final ConversionService conversionService;
    protected final List<Data> entities;
    protected final boolean insert;
    protected final boolean hasGeneratedId;

    /**
     * Default constructor.
     *
     * @param ctx                 The context
     * @param cascadeOperations   The cascade operations
     * @param conversionService   The conversion service
     * @param entityEventListener The entity event listener
     * @param persistentEntity    The persistent entity
     * @param entities            The entities
     * @param insert              The insert
     */
    protected AbstractSyncEntitiesOperations(Ctx ctx,
                                             SyncCascadeOperations<Ctx> cascadeOperations,
                                             ConversionService conversionService,
                                             EntityEventListener<Object> entityEventListener,
                                             RuntimePersistentEntity<T> persistentEntity,
                                             Iterable<T> entities,
                                             boolean insert) {
        super(entityEventListener, persistentEntity, conversionService);
        this.cascadeOperations = cascadeOperations;
        this.conversionService = conversionService;
        this.ctx = ctx;
        this.insert = insert;
        this.hasGeneratedId = insert && persistentEntity.getIdentity() != null && persistentEntity.getIdentity().isGenerated();
        Objects.requireNonNull(entities, "Entities cannot be null");
        Stream<T> stream;
        if (entities instanceof Collection) {
            stream = ((Collection) entities).stream();
        } else {
            stream = CollectionUtils.iterableToList(entities).stream();
        }
        this.entities = stream.map(entity -> {
            Data d = new Data();
            d.entity = entity;
            return d;
        }).collect(Collectors.toList());
    }

    @Override
    protected void cascadePre(Relation.Cascade cascadeType) {
        for (Data d : entities) {
            if (d.vetoed) {
                continue;
            }
            d.entity = cascadeOperations.cascadeEntity(ctx, d.entity, persistentEntity, false, cascadeType);
        }
    }

    @Override
    protected void cascadePost(Relation.Cascade cascadeType) {
        for (Data d : entities) {
            if (d.vetoed) {
                continue;
            }
            d.entity = cascadeOperations.cascadeEntity(ctx, d.entity, persistentEntity, true, cascadeType);
        }
    }

    @Override
    protected void collectAutoPopulatedPreviousValues() {
    }

    @Override
    public void veto(Predicate<T> predicate) {
        for (Data d : entities) {
            if (d.vetoed) {
                continue;
            }
            d.vetoed = predicate.test(d.entity);
        }
    }

    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        boolean allVetoed = true;
        for (Data d : entities) {
            if (d.vetoed) {
                continue;
            }
            final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, d.entity);
            if (!fn.apply((EntityEventContext<Object>) event)) {
                d.vetoed = true;
                continue;
            }
            d.entity = event.getEntity();
            allVetoed = false;
        }
        return allVetoed;
    }

    @Override
    protected void triggerPost(Consumer<EntityEventContext<Object>> fn) {
        for (Data d : entities) {
            if (d.vetoed) {
                continue;
            }
            final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, d.entity);
            fn.accept((EntityEventContext<Object>) event);
            d.entity = event.getEntity();
        }
    }

    @Override
    public List<T> getEntities() {
        return entities.stream().map(d -> d.entity).collect(Collectors.toList());
    }

    @SuppressWarnings("VisibilityModifier")
    protected class Data {
        public T entity;
        public Map<QueryParameterBinding, Object> previousValues;
        public boolean vetoed = false;
    }
}
