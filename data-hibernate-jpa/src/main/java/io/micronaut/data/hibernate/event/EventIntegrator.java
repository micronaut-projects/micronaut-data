/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.hibernate.event;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractPreDatabaseOperationEvent;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Integrates event handling.
 *
 * @author graemerocher
 * @since 1.0
 */
@Primary
@Singleton
@Internal
public class EventIntegrator implements Integrator {

    private final RuntimeEntityRegistry entityRegistry;

    /**
     * Constructor.
     *
     * @param entityRegistry the entity registry
     */
    public EventIntegrator(RuntimeEntityRegistry entityRegistry) {
        this.entityRegistry = entityRegistry;
    }

    @Override
    public void integrate(
            Metadata metadata,
            BootstrapContext bootstrapContext,
            SessionFactoryImplementor sessionFactory) {
        EventListenerRegistry eventListenerRegistry =
                sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        final EntityEventListener<Object> entityEventListener = entityRegistry.getEntityEventListener();
        eventListenerRegistry.getEventListenerGroup(EventType.PRE_INSERT)
                .appendListener(event -> {
                    Class mappedClass = event.getPersister().getMappedClass();
                    if (isNotSupportedMappedClass(mappedClass)) {
                        return false;
                    }
                    final RuntimePersistentEntity<Object> entity = entityRegistry.getEntity(mappedClass);
                    if (entity.hasPrePersistEventListeners()) {
                        Object[] state = event.getState();
                        final DefaultEntityEventContext<Object> context = new StatefulHibernateEventContext<>(entity, event, state);
                        return !entityEventListener.prePersist(context);
                    }
                    return false;
                });

        eventListenerRegistry.getEventListenerGroup(EventType.POST_INSERT)
                .appendListener(new PostInsertEventListener() {
                    @Override
                    public void onPostInsert(PostInsertEvent event) {
                        Class mappedClass = event.getPersister().getMappedClass();
                        if (isNotSupportedMappedClass(mappedClass)) {
                            return;
                        }
                        final RuntimePersistentEntity<Object> entity = entityRegistry.getEntity(mappedClass);
                        if (entity.hasPostPersistEventListeners()) {
                            final DefaultEntityEventContext<Object> context = new SimpleHibernateEventContext<>(entity, event.getEntity());
                            entityEventListener.postPersist(context);
                        }
                    }

                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        return false;
                    }
                });

        eventListenerRegistry.getEventListenerGroup(EventType.PRE_DELETE)
                .appendListener(event -> {
                    Class mappedClass = event.getPersister().getMappedClass();
                    if (isNotSupportedMappedClass(mappedClass)) {
                        return false;
                    }
                    final RuntimePersistentEntity<Object> entity = entityRegistry.getEntity(mappedClass);
                    if (entity.hasPreRemoveEventListeners()) {
                        Object[] state = event.getDeletedState();
                        final DefaultEntityEventContext<Object> context = new StatefulHibernateEventContext<>(entity, event, state);
                        return !entityEventListener.preRemove(context);
                    }
                    return false;
                });

        eventListenerRegistry.getEventListenerGroup(EventType.POST_DELETE)
                .appendListener(new PostDeleteEventListener() {
                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        return false;
                    }

                    @Override
                    public void onPostDelete(PostDeleteEvent event) {
                        Class mappedClass = event.getPersister().getMappedClass();
                        if (isNotSupportedMappedClass(mappedClass)) {
                            return;
                        }
                        final RuntimePersistentEntity<Object> entity = entityRegistry.getEntity(mappedClass);
                        if (entity.hasPostRemoveEventListeners()) {
                            final DefaultEntityEventContext<Object> context = new SimpleHibernateEventContext<>(entity, event.getEntity());
                            entityEventListener.postRemove(context);
                        }
                    }
                });

        eventListenerRegistry.getEventListenerGroup(EventType.PRE_UPDATE)
                .appendListener(event -> {
                    Class mappedClass = event.getPersister().getMappedClass();
                    if (isNotSupportedMappedClass(mappedClass)) {
                        return false;
                    }
                    final RuntimePersistentEntity<Object> entity = entityRegistry.getEntity(mappedClass);
                    if (entity.hasPreUpdateEventListeners()) {
                        Object[] state = event.getState();
                        final DefaultEntityEventContext<Object> context = new StatefulHibernateEventContext<>(entity, event, state);
                        return !entityEventListener.preUpdate(context);
                    }
                    return false;
                });

        eventListenerRegistry.getEventListenerGroup(EventType.POST_UPDATE)
                .appendListener(new PostUpdateEventListener() {
                    @Override
                    public boolean requiresPostCommitHandling(EntityPersister persister) {
                        return false;
                    }

                    @Override
                    public void onPostUpdate(PostUpdateEvent event) {
                        Class mappedClass = event.getPersister().getMappedClass();
                        if (isNotSupportedMappedClass(mappedClass)) {
                            return;
                        }
                        final RuntimePersistentEntity<Object> entity = entityRegistry.getEntity(mappedClass);
                        if (entity.hasPostUpdateEventListeners()) {
                            final DefaultEntityEventContext<Object> context = new SimpleHibernateEventContext<>(entity, event.getEntity());
                            entityEventListener.postUpdate(context);
                        }
                    }
                });

        eventListenerRegistry.getEventListenerGroup(EventType.POST_LOAD)
                .appendListener(event -> {
                    Class mappedClass = event.getPersister().getMappedClass();
                    if (isNotSupportedMappedClass(mappedClass)) {
                        return;
                    }
                    final RuntimePersistentEntity<Object> entity = entityRegistry.getEntity(mappedClass);
                    if (entity.hasPostLoadEventListeners()) {
                        final DefaultEntityEventContext<Object> context = new SimpleHibernateEventContext<>(entity, event.getEntity());
                        entityEventListener.postLoad(context);
                    }
                });
    }

    private static boolean isNotSupportedMappedClass(Class<?> clazz) {
        return !BeanIntrospector.SHARED.findIntrospection(clazz).isPresent();
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        // no-op
    }

    private static final class StatefulHibernateEventContext<T> extends DefaultEntityEventContext<T> {
        private final AbstractPreDatabaseOperationEvent event;
        @Nullable
        private final Object[] state;

        private StatefulHibernateEventContext(RuntimePersistentEntity<T> entity, AbstractPreDatabaseOperationEvent event, Object[] state) {
            super(entity, (T) event.getEntity());
            this.event = event;
            this.state = state;
        }

        @Override
        public <P> void setProperty(BeanProperty<T, P> property, @Nullable P newValue) {
            super.setProperty(property, newValue);
            int i = -1;
            String[] propertyNames = event.getPersister().getPropertyNames();
            for (int idx = 0; idx < propertyNames.length; idx++) {
                if (propertyNames[idx].equals(property.getName())) {
                    i = idx;
                    break;
                }
            }
            if (i >= 0) {
                state[i] = newValue;
            }
        }

        @Override
        public boolean supportsEventSystem() {
            return false;
        }
    }

    private static final class SimpleHibernateEventContext<T> extends DefaultEntityEventContext<T> {
        public SimpleHibernateEventContext(RuntimePersistentEntity<T> entity, T object) {
            super(entity, object);
        }

        @Override
        public boolean supportsEventSystem() {
            return false;
        }
    }
}
