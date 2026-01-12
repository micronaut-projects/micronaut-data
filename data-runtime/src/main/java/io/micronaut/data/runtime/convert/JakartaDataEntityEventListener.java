/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.runtime.convert;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.type.Argument;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.inject.BeanDefinition;
import jakarta.data.event.LifecycleEvent;
import jakarta.data.event.PostDeleteEvent;
import jakarta.data.event.PostInsertEvent;
import jakarta.data.event.PostUpdateEvent;
import jakarta.data.event.PreDeleteEvent;
import jakarta.data.event.PreInsertEvent;
import jakarta.data.event.PreUpdateEvent;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The event listener for Jakarta Data events.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Requires(classes = LifecycleEvent.class)
@Singleton
final class JakartaDataEntityEventListener implements EntityEventListener<Object> {

    private final Map<Class<?>, EventListeners<?>> eventListeners;

    JakartaDataEntityEventListener(BeanContext beanContext) {
        Map<Class<?>, EventListeners<?>> eventListeners = new HashMap<>();
        for (BeanRegistration<ApplicationEventListener> reg : beanContext.getBeanRegistrations(Argument.of(ApplicationEventListener.class, LifecycleEvent.class), null)) {
            BeanDefinition<ApplicationEventListener> definition = reg.definition();
            Optional<Argument<?>> firstTypeVariable = definition.getTypeArguments(ApplicationEventListener.class).stream().findFirst();
            if (firstTypeVariable.isEmpty()) {
                continue;
            }
            Argument<?> eventType = firstTypeVariable.get();
            firstTypeVariable = eventType.getFirstTypeVariable();
            if (firstTypeVariable.isEmpty()) {
                continue;
            }
            Class<?> beanType = firstTypeVariable.get().getType();
            EventListeners<?> listeners = eventListeners.computeIfAbsent(beanType, (t) -> new EventListeners<>());
            Class<?> eventTypeClass = eventType.getType();
            ApplicationEventListener bean = reg.getBean();
            if (eventTypeClass == PreInsertEvent.class) {
                listeners.preInsert.add(bean);
            } else if (eventTypeClass == PostInsertEvent.class) {
                listeners.postInsert.add(bean);
            } else if (eventTypeClass == PreUpdateEvent.class) {
                listeners.preUpdate.add(bean);
            } else if (eventTypeClass == PostUpdateEvent.class) {
                listeners.postUpdate.add(bean);
            } else if (eventTypeClass == PreDeleteEvent.class) {
                listeners.preDelete.add(bean);
            } else if (eventTypeClass == PostDeleteEvent.class) {
                listeners.postDelete.add(bean);
            } else {
                throw new IllegalStateException("Unsupported event type: " + eventTypeClass);
            }
        }
        this.eventListeners = eventListeners;
    }

    @Override
    public boolean supports(RuntimePersistentEntity<Object> entity, Class<? extends Annotation> eventType) {
        return eventListeners.containsKey(entity.getIntrospection().getBeanType());
    }

    @Override
    public boolean prePersist(EntityEventContext<Object> context) {
        getListeners(context).preInsert.forEach(it -> it.onApplicationEvent(new PreInsertEvent<>(context.getEntity())));
        return true;
    }

    @Override
    public void postPersist(EntityEventContext<Object> context) {
        getListeners(context).postInsert.forEach(it -> it.onApplicationEvent(new PostInsertEvent<>(context.getEntity())));
    }

    @Override
    public boolean preRemove(EntityEventContext<Object> context) {
        getListeners(context).preDelete.forEach(it -> it.onApplicationEvent(new PreDeleteEvent<>(context.getEntity())));
        return true;
    }

    @Override
    public void postRemove(EntityEventContext<Object> context) {
        getListeners(context).postDelete.forEach(it -> it.onApplicationEvent(new PostDeleteEvent<>(context.getEntity())));
    }

    @Override
    public boolean preUpdate(EntityEventContext<Object> context) {
        getListeners(context).preUpdate.forEach(it -> it.onApplicationEvent(new PreUpdateEvent<>(context.getEntity())));
        return true;
    }

    @Override
    public void postUpdate(EntityEventContext<Object> context) {
        getListeners(context).postUpdate.forEach(it -> it.onApplicationEvent(new PostUpdateEvent<>(context.getEntity())));
    }

    private <T> EventListeners<T> getListeners(EntityEventContext<T> context) {
        return Objects.requireNonNull(
            (EventListeners<T>) eventListeners.get(context.getPersistentEntity().getIntrospection().getBeanType())
        );
    }

    private record EventListeners<T>(
        List<ApplicationEventListener<PreInsertEvent<T>>> preInsert,
        List<ApplicationEventListener<PostInsertEvent<T>>> postInsert,
        List<ApplicationEventListener<PreUpdateEvent<T>>> preUpdate,
        List<ApplicationEventListener<PostUpdateEvent<T>>> postUpdate,
        List<ApplicationEventListener<PreDeleteEvent<T>>> preDelete,
        List<ApplicationEventListener<PostDeleteEvent<T>>> postDelete
    ) {
        private EventListeners() {
            this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

}
