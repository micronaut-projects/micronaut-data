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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.event.*;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.event.EntityEventRegistry;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry for entities looking up instances of {@link RuntimeEntityRegistry}.
 *
 * @author graemerocher
 * @since 2.3.0
 */
@Singleton
@Internal
public class DefaultRuntimeEntityRegistry implements RuntimeEntityRegistry {
    private final Map<Class, RuntimePersistentEntity> entities = new ConcurrentHashMap<>(10);

    private final EntityEventRegistry eventRegistry;

    /**
     * Default constructor.
     * @param eventRegistry The event registry
     */
    public DefaultRuntimeEntityRegistry(EntityEventRegistry eventRegistry) {
        this.eventRegistry = eventRegistry;
    }

    @NonNull
    @Override
    public EntityEventListener<Object> getEntityEventListener() {
        return eventRegistry;
    }

    @NonNull
    @Override
    public final <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        RuntimePersistentEntity<T> entity = entities.get(type);
        if (entity == null) {
            entity = newEntity(type);
            entities.put(type, entity);
        }
        return entity;
    }

    @NonNull
    @Override
    public <T> RuntimePersistentEntity<T> newEntity(@NonNull Class<T> type) {
        return new RuntimePersistentEntity<T>(type) {
            final boolean hasPrePersistEventListeners = eventRegistry.supports((RuntimePersistentEntity) this, PrePersist.class);
            final boolean hasPreRemoveEventListeners = eventRegistry.supports((RuntimePersistentEntity) this, PreRemove.class);
            final boolean hasPreUpdateEventListeners = eventRegistry.supports((RuntimePersistentEntity) this, PreUpdate.class);
            final boolean hasPostPersistEventListeners = eventRegistry.supports((RuntimePersistentEntity) this, PostPersist.class);
            final boolean hasPostRemoveEventListeners = eventRegistry.supports((RuntimePersistentEntity) this, PostRemove.class);
            final boolean hasPostUpdateEventListeners = eventRegistry.supports((RuntimePersistentEntity) this, PostUpdate.class);
            final boolean hasPostLoadEventListeners = eventRegistry.supports((RuntimePersistentEntity) this, PostLoad.class);

            @Override
            protected RuntimePersistentEntity<T> getEntity(Class<T> type) {
                return DefaultRuntimeEntityRegistry.this.getEntity(type);
            }

            @Override
            public boolean hasPostUpdateEventListeners() {
                return hasPostUpdateEventListeners;
            }

            @Override
            public boolean hasPostRemoveEventListeners() {
                return hasPostRemoveEventListeners;
            }

            @Override
            public boolean hasPostLoadEventListeners() {
                return hasPostLoadEventListeners;
            }

            @Override
            public boolean hasPrePersistEventListeners() {
                return hasPrePersistEventListeners;
            }

            @Override
            public boolean hasPreUpdateEventListeners() {
                return hasPreUpdateEventListeners;
            }

            @Override
            public boolean hasPreRemoveEventListeners() {
                return hasPreRemoveEventListeners;
            }

            @Override
            public boolean hasPostPersistEventListeners() {
                return hasPostPersistEventListeners;
            }
        };
    }
}
