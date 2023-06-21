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
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The base entity operations class.
 *
 * @param <T>   The entity type
 * @param <Exc> The exception
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
abstract class BaseOperations<T, Exc extends Exception> {

    protected final EntityEventListener<Object> entityEventListener;
    protected final RuntimePersistentEntity<T> persistentEntity;
    protected final ConversionService conversionService;

    BaseOperations(EntityEventListener<Object> entityEventListener,
                   RuntimePersistentEntity<T> persistentEntity,
                   ConversionService conversionService) {
        this.entityEventListener = entityEventListener;
        this.persistentEntity = persistentEntity;
        this.conversionService = conversionService;
    }

    /**
     * Compare the expected modifications and the received rows count. If not equals throw {@link OptimisticLockException}.
     *
     * @param expected The expected value
     * @param received THe received value
     */
    protected void checkOptimisticLocking(long expected, long received) {
        if (received != expected) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + expected + " got: " + received);
        }
    }

    /**
     * Persist one operation.
     */
    public void persist() {
        try {
            boolean vetoed = triggerPrePersist();
            if (vetoed) {
                return;
            }
            boolean cascades = persistentEntity.cascadesPersist();
            if (cascades) {
                cascadePre(Relation.Cascade.PERSIST);
            }
            execute();
            triggerPostPersist();
            if (cascades) {
                cascadePost(Relation.Cascade.PERSIST);
            }
        } catch (Exception e) {
            failed(e, "PERSIST");
        }
    }

    /**
     * Delete one operation.
     */
    public void delete() {
        collectAutoPopulatedPreviousValues();
        boolean vetoed = triggerPreRemove();
        if (vetoed) {
            // operation vetoed
            return;
        }
        try {
            execute();
            triggerPostRemove();
        } catch (OptimisticLockException ex) {
            throw ex;
        } catch (Exception e) {
            failed(e, "DELETE");
        }
    }

    /**
     * Update one operation.
     */
    public void update() {
        collectAutoPopulatedPreviousValues();
        boolean vetoed = triggerPreUpdate();
        if (vetoed) {
            return;
        }
        try {
            boolean cascades = persistentEntity.cascadesUpdate();
            if (cascades) {
                cascadePre(Relation.Cascade.UPDATE);
            }
            execute();
            triggerPostUpdate();
            if (cascades) {
                cascadePost(Relation.Cascade.UPDATE);
            }
        } catch (OptimisticLockException ex) {
            throw ex;
        } catch (Exception e) {
            failed(e, "UPDATE");
        }
    }

    protected void failed(Exception e, String operation) throws DataAccessException {
        throw new DataAccessException("Error executing " + operation + ": " + e.getMessage(), e);
    }

    /**
     * Cascade pre operation.
     *
     * @param cascadeType The cascade type
     */
    protected abstract void cascadePre(Relation.Cascade cascadeType);

    /**
     * Cascade post operation.
     *
     * @param cascadeType The cascade type
     */
    protected abstract void cascadePost(Relation.Cascade cascadeType);

    /**
     * Collect auto-populated values before pre-triggers modifies them.
     */
    protected abstract void collectAutoPopulatedPreviousValues();

    /**
     * Execute update.
     *
     * @throws Exc The exception
     */
    protected abstract void execute() throws Exc;

    /**
     * Veto an entity.
     *
     * @param predicate The veto predicate
     */
    public abstract void veto(Predicate<T> predicate);

    /**
     * Update entity id.
     *
     * @param identity The identity property.
     * @param entity   The entity instance
     * @param id       The id instance
     * @return The entity instance
     */
    protected T updateEntityId(BeanProperty<T, Object> identity, T entity, Object id) {
        if (id == null) {
            return entity;
        }
        if (identity.getType().isInstance(id)) {
            return setProperty(identity, entity, id);
        }
        return convertAndSetWithValue(identity, entity, id);
    }

    /**
     * Trigger the pre persist event.
     *
     * @return true if operation was vetoed
     */
    protected boolean triggerPrePersist() {
        if (!persistentEntity.hasPrePersistEventListeners()) {
            return false;
        }
        return triggerPre(entityEventListener::prePersist);
    }

    /**
     * Trigger the pre update event.
     *
     * @return true if operation was vetoed
     */
    protected boolean triggerPreUpdate() {
        if (!persistentEntity.hasPreUpdateEventListeners()) {
            return false;
        }
        return triggerPre(entityEventListener::preUpdate);
    }

    /**
     * Trigger the pre remove event.
     *
     * @return true if operation was vetoed
     */
    protected boolean triggerPreRemove() {
        if (!persistentEntity.hasPreRemoveEventListeners()) {
            return false;
        }
        return triggerPre(entityEventListener::preRemove);
    }

    /**
     * Trigger the post update event.
     */
    protected void triggerPostUpdate() {
        if (!persistentEntity.hasPostUpdateEventListeners()) {
            return;
        }
        triggerPost(entityEventListener::postUpdate);
    }

    /**
     * Trigger the post remove event.
     */
    protected void triggerPostRemove() {
        if (!persistentEntity.hasPostRemoveEventListeners()) {
            return;
        }
        triggerPost(entityEventListener::postRemove);
    }

    /**
     * Trigger the post persist event.
     */
    protected void triggerPostPersist() {
        if (!persistentEntity.hasPostPersistEventListeners()) {
            return;
        }
        triggerPost(entityEventListener::postPersist);
    }

    /**
     * Trigger pre-actions on {@link EntityEventContext}.
     *
     * @param fn The entity context function
     * @return true if operation was vetoed
     */
    protected abstract boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn);

    /**
     * Trigger post-actions on {@link EntityEventContext}.
     *
     * @param fn The entity context function
     */
    protected abstract void triggerPost(Consumer<EntityEventContext<Object>> fn);

    private <X, Y> X setProperty(BeanProperty<X, Y> beanProperty, X x, Y y) {
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(x, y);
        }
        beanProperty.set(x, y);
        return x;
    }

    private <B, L> B convertAndSetWithValue(BeanProperty<B, L> beanProperty, B bean, L value) {
        Argument<L> argument = beanProperty.asArgument();
        final ArgumentConversionContext<L> context = ConversionContext.of(argument);
        L convertedValue = conversionService.convert(value, context).orElseThrow(() ->
                new ConversionErrorException(argument, context.getLastError()
                        .orElse(() -> new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + beanProperty.getType())))
        );
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(bean, convertedValue);
        }
        beanProperty.set(bean, convertedValue);
        return bean;
    }
}
