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
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.hibernate.tuple.entity.EntityMetamodel;

import jakarta.inject.Singleton;

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
            SessionFactoryImplementor sessionFactory,
            SessionFactoryServiceRegistry serviceRegistry) {
        EventListenerRegistry eventListenerRegistry =
                serviceRegistry.getService(EventListenerRegistry.class);
        final EntityEventListener<Object> entityEventListener = entityRegistry.getEntityEventListener();
        eventListenerRegistry.getEventListenerGroup(EventType.PRE_INSERT)
                .appendListener((PreInsertEventListener) event -> {
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
                .appendListener((PreDeleteEventListener) event -> {
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
                .appendListener((PreUpdateEventListener) event -> {
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
                .appendListener((PostLoadEventListener) event -> {
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

    private static class StatefulHibernateEventContext<T> extends DefaultEntityEventContext<T> {
        private final AbstractPreDatabaseOperationEvent event;
        private final Object[] state;

        public StatefulHibernateEventContext(RuntimePersistentEntity<T> entity, AbstractPreDatabaseOperationEvent event, Object[] state) {
            super(entity, (T) event.getEntity());
            this.event = event;
            this.state = state;
        }

        @Override
        public <P> void setProperty(BeanProperty<T, P> property, P newValue) {
            super.setProperty(property, newValue);
            EntityMetamodel entityMetamodel = event.getPersister().getEntityMetamodel();
            int i = entityMetamodel.getPropertyIndex(property.getName());
            state[i] = newValue;
        }

        @Override
        public final boolean supportsEventSystem() {
            return false;
        }
    }

    private static class SimpleHibernateEventContext<T> extends DefaultEntityEventContext<T> {
        public SimpleHibernateEventContext(RuntimePersistentEntity<T> entity, T object) {
            super(entity, (T) object);
        }

        @Override
        public final boolean supportsEventSystem() {
            return false;
        }
    }
}
