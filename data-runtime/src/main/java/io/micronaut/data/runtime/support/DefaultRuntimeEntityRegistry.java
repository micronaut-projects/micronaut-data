/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.support;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.event.*;
import io.micronaut.data.model.runtime.PropertyAutoPopulator;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.event.EntityEventRegistry;

import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry for entities looking up instances of {@link RuntimeEntityRegistry}.
 *
 * @author graemerocher
 * @since 2.3.0
 */
@Singleton
@Internal
final class DefaultRuntimeEntityRegistry implements RuntimeEntityRegistry, ApplicationContextProvider {
    private final Map<Class, RuntimePersistentEntity> entities = new ConcurrentHashMap<>(10);
    private final Map<Class<? extends Annotation>, PropertyAutoPopulator<?>> propertyPopulators;
    private final EntityEventRegistry eventRegistry;
    private final ApplicationContext applicationContext;
    private final AttributeConverterRegistry attributeConverterRegistry;

    /**
     * Default constructor.
     *
     * @param eventRegistry         The event registry
     * @param propertyPopulators    The property populators
     * @param applicationContext    The application context
     * @param attributeConverterRegistry The type converter registry
     */
    public DefaultRuntimeEntityRegistry(
            EntityEventRegistry eventRegistry,
            Collection<BeanRegistration<PropertyAutoPopulator<?>>> propertyPopulators,
            ApplicationContext applicationContext,
            AttributeConverterRegistry attributeConverterRegistry) {
        this.eventRegistry = eventRegistry;
        this.propertyPopulators = new HashMap<>(propertyPopulators.size());
        this.attributeConverterRegistry = attributeConverterRegistry;
        for (BeanRegistration<PropertyAutoPopulator<?>> propertyPopulator : propertyPopulators) {
            final PropertyAutoPopulator<?> populator = propertyPopulator.getBean();
            final List<Argument<?>> typeArguments = propertyPopulator.getBeanDefinition().getTypeArguments(PropertyAutoPopulator.class);
            if (!typeArguments.isEmpty()) {
                @SuppressWarnings("unchecked") final Class<? extends Annotation> annotationType =
                        (Class<? extends Annotation>) typeArguments.iterator().next().getType();
                if (this.propertyPopulators.containsKey(annotationType)) {
                    throw new IllegalStateException("Multiple property populators for annotation of type are not allowed: " + annotationType);
                } else {
                    this.propertyPopulators.put(annotationType, populator);
                }
            }
        }
        this.applicationContext = applicationContext;
    }

    @Override
    @NonNull
    public Object autoPopulateRuntimeProperty(@NonNull RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
        for (Map.Entry<Class<? extends Annotation>, PropertyAutoPopulator<?>> entry : propertyPopulators.entrySet()) {
            if (persistentProperty.getAnnotationMetadata().hasAnnotation(entry.getKey())) {
                final PropertyAutoPopulator<?> populator = entry.getValue();
                return Objects.requireNonNull(
                        populator.populate(persistentProperty, previousValue),
                        () -> "PropertyAutoPopulator illegally returned null: " + populator.getClass()
                );
            }
        }
        throw new IllegalStateException("Cannot auto populate property: " + persistentProperty.getName()  + " for entity: " + persistentProperty.getOwner().getName());
    }

    @NonNull
    @Override
    public EntityEventListener<Object> getEntityEventListener() {
        return eventRegistry;
    }

    @NonNull
    @Override
    public <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        // we need atomicity here, since entites are compared by identity (==)
        return entities.computeIfAbsent(type, this::newEntity);
    }

    @NonNull
    @Override
    public <T> RuntimePersistentEntity<T> newEntity(@NonNull Class<T> type) {
        return new RuntimePersistentEntity<>(type) {
            private volatile Boolean prePersistSupported;
            private volatile Boolean preRemoveSupported;
            private volatile Boolean preUpdateSupported;
            private volatile Boolean postPersistSupported;
            private volatile Boolean postRemoveSupported;
            private volatile Boolean postUpdateSupported;
            private volatile Boolean postLoadSupported;

            @Override
            protected AttributeConverter<Object, Object> resolveConverter(Class<?> converterClass) {
                return attributeConverterRegistry.getConverter(converterClass);
            }

            @Override
            protected RuntimePersistentEntity<T> getEntity(Class<T> type) {
                return DefaultRuntimeEntityRegistry.this.getEntity(type);
            }

            @Override
            public boolean hasPostUpdateEventListeners() {
                Boolean v = postUpdateSupported;
                if (v == null) {
                    synchronized (this) {
                        v = postUpdateSupported;
                        if (v == null) {
                            v = eventRegistry.supports((RuntimePersistentEntity) this, PostUpdate.class);
                            postUpdateSupported = v;
                        }
                    }
                }
                return v;
            }

            @Override
            public boolean hasPostRemoveEventListeners() {
                Boolean v = postRemoveSupported;
                if (v == null) {
                    synchronized (this) {
                        v = postRemoveSupported;
                        if (v == null) {
                            v = eventRegistry.supports((RuntimePersistentEntity) this, PostRemove.class);
                            postRemoveSupported = v;
                        }
                    }
                }
                return v;
            }

            @Override
            public boolean hasPostLoadEventListeners() {
                Boolean v = postLoadSupported;
                if (v == null) {
                    synchronized (this) {
                        v = postLoadSupported;
                        if (v == null) {
                            v = eventRegistry.supports((RuntimePersistentEntity) this, PostLoad.class);
                            postLoadSupported = v;
                        }
                    }
                }
                return v;
            }

            @Override
            public boolean hasPrePersistEventListeners() {
                Boolean v = prePersistSupported;
                if (v == null) {
                    synchronized (this) {
                        v = prePersistSupported;
                        if (v == null) {
                            v = eventRegistry.supports((RuntimePersistentEntity) this, PrePersist.class);
                            prePersistSupported = v;
                        }
                    }
                }
                return v;
            }

            @Override
            public boolean hasPreUpdateEventListeners() {
                Boolean v = preUpdateSupported;
                if (v == null) {
                    synchronized (this) {
                        v = preUpdateSupported;
                        if (v == null) {
                            v = eventRegistry.supports((RuntimePersistentEntity) this, PreUpdate.class);
                            preUpdateSupported = v;
                        }
                    }
                }
                return v;
            }

            @Override
            public boolean hasPreRemoveEventListeners() {
                Boolean v = preRemoveSupported;
                if (v == null) {
                    synchronized (this) {
                        v = preRemoveSupported;
                        if (v == null) {
                            v = eventRegistry.supports((RuntimePersistentEntity) this, PreRemove.class);
                            preRemoveSupported = v;
                        }
                    }
                }
                return v;
            }

            @Override
            public boolean hasPostPersistEventListeners() {
                Boolean v = postPersistSupported;
                if (v == null) {
                    synchronized (this) {
                        v = postPersistSupported;
                        if (v == null) {
                            v = eventRegistry.supports((RuntimePersistentEntity) this, PostPersist.class);
                            postPersistSupported = v;
                        }
                    }
                }
                return v;
            }
        };
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
