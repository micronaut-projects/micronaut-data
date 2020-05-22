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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.runtime.date.DateTimeProvider;
import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractPreDatabaseOperationEvent;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.hibernate.tuple.entity.EntityMetamodel;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Integrates event handling.
 *
 * @author graemerocher
 * @since 1.0
 */
@Primary
@Singleton
public class EventIntegrator implements Integrator {

    private final DateTimeProvider dateTimeProvider;

    /**
     * Constructor.
     *
     * @param dateTimeProvider dependency that will provide the time.
     */
    public EventIntegrator(@NotNull DateTimeProvider dateTimeProvider) {
        this.dateTimeProvider = dateTimeProvider;
    }

    @Override
    public void integrate(
            Metadata metadata,
            SessionFactoryImplementor sessionFactory,
            SessionFactoryServiceRegistry serviceRegistry) {
        EventListenerRegistry eventListenerRegistry =
                serviceRegistry.getService(EventListenerRegistry.class);

        Collection<PersistentClass> entityBindings = metadata.getEntityBindings();
        Map<Class, BeanProperty> lastUpdates = new HashMap<>(entityBindings.size());
        Map<Class, BeanProperty> dateCreated = new HashMap<>(entityBindings.size());

        entityBindings.forEach(e -> {
                    Class<?> mappedClass = e.getMappedClass();
                    if (mappedClass != null) {
                        BeanIntrospection<?> introspection = BeanIntrospector.SHARED.findIntrospection(mappedClass).orElse(null);
                        if (introspection != null) {
                            introspection.getIndexedProperty(DateCreated.class).ifPresent(bp ->
                                    dateCreated.put(mappedClass, bp)
                            );
                            introspection.getIndexedProperty(DateUpdated.class).ifPresent(bp ->
                                    lastUpdates.put(mappedClass, bp)
                            );
                        }
                    }
                }
        );

        ConversionService<?> conversionService = ConversionService.SHARED;

        if (CollectionUtils.isNotEmpty(dateCreated)) {

            eventListenerRegistry.getEventListenerGroup(EventType.PRE_INSERT)
                    .appendListener((PreInsertEventListener) event -> {
                        Object[] state = event.getState();
                        timestampIfNecessary(
                                dateCreated,
                                lastUpdates,
                                conversionService,
                                event,
                                state,
                                true
                        );
                        return false;
                    });
        }

        if (CollectionUtils.isNotEmpty(lastUpdates)) {

            eventListenerRegistry.getEventListenerGroup(EventType.PRE_UPDATE)
                    .appendListener((PreUpdateEventListener) event -> {
                        timestampIfNecessary(
                                dateCreated, lastUpdates,
                                conversionService,
                                event,
                                event.getState(),
                                false
                        );
                        return false;
                    });
        }
    }

    private void timestampIfNecessary(
            Map<Class, BeanProperty> dateCreated,
            Map<Class, BeanProperty> lastUpdates,
            ConversionService<?> conversionService,
            AbstractPreDatabaseOperationEvent event,
            Object[] state,
            boolean isInsert) {
        Object entity = event.getEntity();
        Object now = null;
        if (isInsert) {
            BeanProperty dateCreatedProp = dateCreated.get(entity.getClass());
            if (dateCreatedProp != null) {
                now = dateTimeProvider.getNow();
                conversionService.convert(now, dateCreatedProp.getType()).ifPresent(o -> {
                            dateCreatedProp.set(entity, o);
                            EntityMetamodel entityMetamodel = event.getPersister().getEntityMetamodel();
                            int i = entityMetamodel.getPropertyIndex(dateCreatedProp.getName());
                            state[i] = o;
                        }

                );
            }
        }

        BeanProperty lastUpdatedProp = lastUpdates.get(entity.getClass());
        if (lastUpdatedProp != null) {
            now = now != null ? now : dateTimeProvider.getNow();
            conversionService.convert(now, lastUpdatedProp.getType()).ifPresent(o -> {
                        lastUpdatedProp.set(entity, o);
                        EntityMetamodel entityMetamodel = event.getPersister().getEntityMetamodel();
                        int i = entityMetamodel.getPropertyIndex(lastUpdatedProp.getName());
                        state[i] = o;
                    }

            );
        }

    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        // no-op
    }
}
