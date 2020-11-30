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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.jdbc.runtime.AutoPopulatedValueProvider;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.runtime.date.DateTimeProvider;

import javax.inject.Singleton;

/**
 * Setter of {@link DateCreated} and {@link DateUpdated} auto populated properties.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Singleton
@Experimental
public class DateCreatedUpdatedAutoPopulatedSetter implements AutoPopulatedValueProvider {

    private final DateTimeProvider dateTimeProvider;

    public DateCreatedUpdatedAutoPopulatedSetter(DateTimeProvider dateTimeProvider) {
        this.dateTimeProvider = dateTimeProvider;
    }

    @Override
    public Object provideOnCreate(PersistentProperty persistentProperty, BeanProperty<Object, Object> beanProperty, Object entity) {
        Object value = beanProperty.get(entity);
        if (value != null) {
            return value;
        }
        Object now = dateTimeProvider.getNow();
        // We should set the same date for created and updated
        for (BeanProperty<Object, Object> bp : beanProperty.getDeclaringBean().getBeanProperties()) {
            if (bp.hasAnnotation(DateCreated.class) || bp.hasAnnotation(DateUpdated.class)) {
                bp.convertAndSet(entity, now);
            }
        }
        return beanProperty.get(entity);
    }

    @Override
    public Object provideOnUpdate(PersistentProperty persistentProperty, Class<?> type, Object previousValue) {
        return converted(type, dateTimeProvider.getNow());
    }

    @Override
    public boolean supportsCreate(PersistentProperty persistentProperty, Class<?> type) {
        AnnotationMetadata annotationMetadata = persistentProperty.getAnnotationMetadata();
        return annotationMetadata.hasAnnotation(DateCreated.class) || annotationMetadata.hasAnnotation(DateUpdated.class);
    }

    @Override
    public boolean supportsUpdate(PersistentProperty persistentProperty, Class<?> type) {
        return persistentProperty.getAnnotationMetadata().hasAnnotation(DateUpdated.class);
    }

    private Object converted(Class<?> type, Object value) {
        return ConversionService.SHARED.convert(value, type).orElseThrow(
                () -> new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + type));
    }

}
