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
package io.micronaut.data.runtime.event.listeners;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Abstract implementation of a listener that handles {@link io.micronaut.data.annotation.AutoPopulated}.
 *
 * @author graemerocher
 * @since 2.3.0
 */
public abstract class AutoPopulatedEntityEventListener implements EntityEventListener<Object> {
    private final Map<RuntimePersistentEntity<Object>, RuntimePersistentProperty<Object>[]> applicableProperties
            = new ConcurrentHashMap<>(30);

    @Override
    public final boolean supports(RuntimePersistentEntity<Object> entity, Class<? extends Annotation> eventType) {
        if (getEventTypes().contains(eventType) && entity.hasAutoPopulatedProperties()) {
            RuntimePersistentProperty<Object>[] properties = applicableProperties.get(entity);
            if (properties == null) {
                final Collection<RuntimePersistentProperty<Object>> persistentProperties = entity.getPersistentProperties();
                List<RuntimePersistentProperty<Object>> propertyList = new ArrayList<>(persistentProperties.size());
                final RuntimePersistentProperty<Object> identity = entity.getIdentity();
                if (identity != null && identity.isAutoPopulated()) {
                    propertyList.add(identity);
                }
                final RuntimePersistentProperty<Object>[] compositeIdentity = entity.getCompositeIdentity();
                if (compositeIdentity != null) {
                    for (RuntimePersistentProperty<Object> compositeId : compositeIdentity) {
                        if (compositeId.isAutoPopulated()) {
                            propertyList.add(compositeId);
                        }
                    }
                }
                propertyList.addAll(persistentProperties.stream()
                        .filter(PersistentProperty::isAutoPopulated)
                        .toList());
                //noinspection unchecked
                properties = propertyList.stream().filter(getPropertyPredicate()).toArray(RuntimePersistentProperty[]::new);
                if (ArrayUtils.isEmpty(properties)) {
                    applicableProperties.put(entity, RuntimePersistentProperty.EMPTY_PROPERTY_ARRAY);
                } else {
                    applicableProperties.put(entity, properties);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * @return The event type
     */
    @NonNull
    protected abstract List<Class<? extends Annotation>> getEventTypes();

    /**
     * @return A predicate to apply for the given property.
     */
    @NonNull
    protected abstract Predicate<RuntimePersistentProperty<Object>> getPropertyPredicate();

    /**
     * Returns the applicable properties for this listener.
     * @param entity The entity
     * @return the properties
     */
    protected @NonNull RuntimePersistentProperty<Object>[] getApplicableProperties(RuntimePersistentEntity<Object> entity) {
        final RuntimePersistentProperty<Object>[] properties = applicableProperties.get(entity);
        if (properties != null) {
            return properties;
        } else {
            return RuntimePersistentProperty.EMPTY_PROPERTY_ARRAY;
        }
    }
}
