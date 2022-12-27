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
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Abstract reactive entity operations.
 *
 * @param <Ctx> The operation context
 * @param <T>   The entity type
 * @param <Exc> The exception
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public abstract class AbstractReactiveEntityOperations<Ctx extends OperationContext, T, Exc extends Exception> extends ReactiveEntityOperations<T, Exc> {

    protected final Ctx ctx;
    protected final ReactiveCascadeOperations<Ctx> cascadeOperations;
    protected final boolean insert;
    protected final boolean hasGeneratedId;
    protected Mono<Data> data;

    /**
     * Default constructor.
     *
     * @param ctx                 The context
     * @param cascadeOperations   The cascade operations
     * @param conversionService   The conversion service
     * @param entityEventListener The entity event listener
     * @param persistentEntity    The persistent entity
     * @param entity              The entity
     * @param insert              The insert
     */
    protected AbstractReactiveEntityOperations(Ctx ctx,
                                               ReactiveCascadeOperations<Ctx> cascadeOperations,
                                               ConversionService conversionService,
                                               EntityEventListener<Object> entityEventListener,
                                               RuntimePersistentEntity<T> persistentEntity,
                                               T entity,
                                               boolean insert) {
        super(entityEventListener, persistentEntity, conversionService);
        this.cascadeOperations = cascadeOperations;
        this.ctx = ctx;
        this.insert = insert;
        this.hasGeneratedId = insert && persistentEntity.getIdentity() != null && persistentEntity.getIdentity().isGenerated();
        Data data = new Data();
        data.entity = entity;
        this.data = Mono.just(data);
    }

    @Override
    protected void cascadePre(Relation.Cascade cascadeType) {
        doCascade(false, cascadeType);
    }

    @Override
    protected void cascadePost(Relation.Cascade cascadeType) {
        doCascade(true, cascadeType);
    }

    private void doCascade(boolean isPost, Relation.Cascade cascadeType) {
        this.data = data.flatMap(d -> {
            if (d.vetoed) {
                return Mono.just(d);
            }
            Mono<T> entity = cascadeOperations.cascadeEntity(ctx, d.entity, persistentEntity, isPost, cascadeType);
            return entity.map(e -> {
                d.entity = e;
                return d;
            });
        });
    }

    @Override
    protected boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn) {
        data = data.map(d -> {
            if (d.vetoed) {
                return d;
            }
            final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, d.entity);
            d.vetoed = !fn.apply((EntityEventContext<Object>) event);
            d.entity = event.getEntity();
            return d;
        });
        return false;
    }

    @Override
    protected void triggerPost(Consumer<EntityEventContext<Object>> fn) {
        data = data.map(d -> {
            if (d.vetoed) {
                return d;
            }
            final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(persistentEntity, d.entity);
            fn.accept((EntityEventContext<Object>) event);
            return d;
        });
    }

    @Override
    public void veto(Predicate<T> predicate) {
        data = data.map(d -> {
            if (d.vetoed) {
                return d;
            }
            d.vetoed = predicate.test(d.entity);
            return d;
        });
    }

    private boolean notVetoed(Data data) {
        return !data.vetoed;
    }

    /**
     * @return The entity
     */
    public Mono<T> getEntity() {
        return data.filter(this::notVetoed).map(d -> d.entity);
    }

    /**
     * @return The rows updated
     */
    public Mono<Number> getRowsUpdated() {
        return data.filter(this::notVetoed).<Number>map(d -> d.rowsUpdated).switchIfEmpty(Mono.just(0L));
    }

    /**
     * Internal entity data holder.
     */
    @SuppressWarnings("VisibilityModifier")
    protected final class Data {
        public T entity;
        public Object filter;
        public Map<QueryParameterBinding, Object> previousValues;
        public long rowsUpdated;
        public boolean vetoed = false;
    }
}
