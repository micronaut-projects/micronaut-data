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
package io.micronaut.data.runtime.autopopulated;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreUpdate;
import io.micronaut.data.autopopulated.EntityAutoPopulatedPropertyProvider;
import io.micronaut.data.event.EntityListener;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.date.DateTimeProvider;

import javax.inject.Singleton;

/**
 * Setter of {@link DateCreated} and {@link DateUpdated} auto populated properties.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Singleton
class DateCreatedUpdatedAutoPopulatedListener implements EntityListener<Object>, EntityAutoPopulatedPropertyProvider {

    private final DateTimeProvider<Object> dateTimeProvider;

    public DateCreatedUpdatedAutoPopulatedListener(DateTimeProvider<Object> dateTimeProvider) {
        this.dateTimeProvider = dateTimeProvider;
    }

    @PrePersist
    void onPrePersist(Object entity) {
        Object now = dateTimeProvider.getNow();
        // We should set the same date for created and updated
        BeanIntrospection<Object> introspection = BeanIntrospection.getIntrospection((Class<Object>) entity.getClass());
        for (BeanProperty<Object, Object> bp : introspection.getBeanProperties()) {
            if (bp.hasAnnotation(DateCreated.class) || bp.hasAnnotation(DateUpdated.class)) {
                bp.convertAndSet(entity, now);
            }
        }
    }

    @PreUpdate
    void onPreUpdate(Object entity) {
        BeanIntrospection<Object> introspection = BeanIntrospection.getIntrospection((Class<Object>) entity.getClass());
        for (BeanProperty<Object, Object> bp : introspection.getBeanProperties()) {
            if (bp.hasAnnotation(DateUpdated.class)) {
                bp.convertAndSet(entity, dateTimeProvider.getNow());
            }
        }
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public Object autoPopulate(RuntimePersistentProperty<?> property, Object previousValue) {
        return ConversionService.SHARED.convertRequired(dateTimeProvider.getNow(), property.getArgument());
    }

    @Override
    public boolean supports(RuntimePersistentProperty<?> property) {
        return property.isAnnotationPresent(DateUpdated.class);
    }
}
