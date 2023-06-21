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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreRemove;
import io.micronaut.data.annotation.event.PreUpdate;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.PropertyAutoPopulator;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.List;

/**
 * Supports optimistic locking by using a version.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
@Singleton
public class VersionGeneratingEntityEventListener implements EntityEventListener<Object>, PropertyAutoPopulator<Version> {

    private static final List<Class<? extends Annotation>> SUPPORTED_EVENTS = Arrays.asList(PrePersist.class, PreUpdate.class, PreRemove.class);

    private final DateTimeProvider dateTimeProvider;
    private final DataConversionService conversionService;

    public VersionGeneratingEntityEventListener(DateTimeProvider dateTimeProvider, DataConversionService conversionService) {
        this.dateTimeProvider = dateTimeProvider;
        this.conversionService = conversionService;
    }

    private boolean shouldSkip(@NonNull EntityEventContext<Object> context) {
        return !context.supportsEventSystem();
    }

    @Override
    public boolean supports(RuntimePersistentEntity<Object> entity, Class<? extends Annotation> eventType) {
        return entity.getVersion() != null && SUPPORTED_EVENTS.contains(eventType);
    }

    @Override
    public boolean prePersist(@NonNull EntityEventContext<Object> context) {
        if (shouldSkip(context)) {
            return true;
        }
        final BeanProperty<Object, Object> property = context.getPersistentEntity().getVersion().getProperty();
        Object newVersion = init(property.getType());
        context.setProperty(property, newVersion);
        return true;
    }

    @Override
    public boolean preUpdate(@NonNull EntityEventContext<Object> context) {
        if (shouldSkip(context)) {
            return true;
        }
        final Object entity = context.getEntity();
        final BeanProperty<Object, Object> property = context.getPersistentEntity().getVersion().getProperty();
        Object newVersion = increment(property.get(entity), property.getType());
        context.setProperty(property, newVersion);
        return true;
    }

    @Override
    public boolean preRemove(@NonNull EntityEventContext<Object> context) {
        return preUpdate(context);
    }

    @Override
    public @NonNull Object populate(RuntimePersistentProperty<?> property, @Nullable Object previousValue) {
        Class<?> type = property.getType();
        return increment(previousValue, type);
    }

    private Object increment(Object previousValue, Class<?> type) {
        if (previousValue == null) {
            throw new IllegalStateException("@Version value cannot be null");
        }
        if (Temporal.class.isAssignableFrom(type)) {
            return newTemporal(type);
        } else if (type == Integer.class) {
            return (Integer) previousValue + 1;
        } else if (type == Long.class) {
            return (Long) previousValue + 1L;
        } else if (type == Short.class) {
            return (Short) previousValue + (short) 1;
        } else {
            throw new DataAccessException("Unsupported @Version type: " + type);
        }
    }

    private Object init(Class<?> valueType) {
        if (Temporal.class.isAssignableFrom(valueType)) {
            return newTemporal(valueType);
        } else if (valueType == Integer.class) {
            return 0;
        } else if (valueType == Long.class) {
            return 0L;
        } else if (valueType == Short.class) {
            return (short) 0;
        } else {
            throw new DataAccessException("Unsupported @Version type: " + valueType);
        }
    }

    private Object newTemporal(Class<?> type) {
        Object now = dateTimeProvider.getNow();
        return conversionService.convertRequired(now, type);
    }

}
