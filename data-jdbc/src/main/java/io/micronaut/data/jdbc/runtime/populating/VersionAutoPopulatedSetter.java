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
package io.micronaut.data.jdbc.runtime.populating;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.runtime.AutoPopulatedValueProvider;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.runtime.date.DateTimeProvider;

import javax.inject.Singleton;
import java.time.temporal.Temporal;

/**
 * Setter of {@link Version} auto populated properties.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Singleton
@Experimental
public class VersionAutoPopulatedSetter implements AutoPopulatedValueProvider {

    private final DateTimeProvider dateTimeProvider;

    public VersionAutoPopulatedSetter(DateTimeProvider dateTimeProvider) {
        this.dateTimeProvider = dateTimeProvider;
    }

    @Override
    public Object provideOnCreate(PersistentProperty persistentProperty, BeanProperty<Object, Object> beanProperty, Object entity) {
        Class<?> valueType = beanProperty.getType();
        if (Temporal.class.isAssignableFrom(valueType)) {
            return dateTimeProvider.getNow();
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

    @Override
    public Object provideOnUpdate(PersistentProperty persistentProperty, Class<?> type, Object previousValue) {
        if (previousValue == null) {
            throw new IllegalStateException("Version cannot be null");
        }
        if (Temporal.class.isAssignableFrom(type)) {
            return dateTimeProvider.getNow();
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

    @Override
    public boolean supportsCreate(PersistentProperty persistentProperty, Class<?> type) {
        return persistentProperty.getAnnotationMetadata().hasAnnotation(Version.NAME);
    }

    @Override
    public boolean supportsUpdate(PersistentProperty persistentProperty, Class<?> type) {
        return persistentProperty.getAnnotationMetadata().hasAnnotation(Version.NAME);
    }

}
