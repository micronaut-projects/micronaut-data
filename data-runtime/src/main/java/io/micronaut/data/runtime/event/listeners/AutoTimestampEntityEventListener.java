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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreUpdate;
import io.micronaut.data.model.runtime.PropertyAutoPopulator;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.date.DateTimeProvider;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * An event listener that handles {@link DateCreated} and {@link DateUpdated}.
 *
 * @author graemerocher
 * @since 2.3.0
 */
@Singleton
public class AutoTimestampEntityEventListener extends AutoPopulatedEntityEventListener implements PropertyAutoPopulator<DateUpdated> {
    private final DateTimeProvider<?> dateTimeProvider;
    private final ConversionService<?> conversionService;

    /**
     * Default constructor.
     * @param dateTimeProvider The date time provider
     * @param conversionService The conversion service
     */
    public AutoTimestampEntityEventListener(DateTimeProvider<?> dateTimeProvider, ConversionService<?> conversionService) {
        this.dateTimeProvider = dateTimeProvider;
        this.conversionService = conversionService;
    }

    @NonNull
    @Override
    protected List<Class<? extends Annotation>> getEventTypes() {
        return Arrays.asList(PrePersist.class, PreUpdate.class);
    }

    @NonNull
    @Override
    protected Predicate<RuntimePersistentProperty<Object>> getPropertyPredicate() {
        return (prop) -> {
            final AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
            return annotationMetadata.hasAnnotation(DateCreated.class) || annotationMetadata.hasAnnotation(DateUpdated.class);
        };
    }

    @Override
    public boolean prePersist(@NonNull EntityEventContext<Object> context) {
        autoTimestampIfNecessary(context, false);
        return true;
    }

    @Override
    public boolean preUpdate(@NonNull EntityEventContext<Object> context) {
        autoTimestampIfNecessary(context, true);
        return true;
    }

    @Override
    @NonNull
    public Object populate(RuntimePersistentProperty<?> property, @Nullable Object previousValue) {
        return ConversionService.SHARED.convertRequired(dateTimeProvider.getNow(), property.getArgument());
    }

    private void autoTimestampIfNecessary(@NonNull EntityEventContext<Object> context, boolean isUpdate) {
        final RuntimePersistentProperty<Object>[] applicableProperties = getApplicableProperties(context.getPersistentEntity());
        final Object now = dateTimeProvider.getNow();
        for (RuntimePersistentProperty<Object> property : applicableProperties) {
            if (isUpdate) {
                if (!property.getAnnotationMetadata().booleanValue(AutoPopulated.class, "updateable").orElse(true)) {
                    continue;
                }
            }

            @SuppressWarnings("unchecked")
            final BeanProperty<Object, Object> beanProperty = (BeanProperty<Object, Object>) property.getProperty();
            final Class<?> propertyType = property.getType();
            if (propertyType.isInstance(now)) {
                context.setProperty(beanProperty, now);
            } else {
                conversionService.convert(now, propertyType).ifPresent(o -> {
                    context.setProperty(beanProperty, o);
                });
            }
        }
    }
}
