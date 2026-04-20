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

import io.micronaut.core.annotation.AnnotationMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreUpdate;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.model.runtime.PropertyAutoPopulator;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
    private final DataConversionService conversionService;

    /**
     * Default constructor.
     * @param dateTimeProvider The date time provider
     * @param conversionService The conversion service
     */
    public AutoTimestampEntityEventListener(DateTimeProvider<?> dateTimeProvider, DataConversionService conversionService) {
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
        return prop -> {
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
        Object now = dateTimeProvider.getNow();
        ChronoUnit truncateToValue = truncateToDateUpdated(property.getAnnotationMetadata());
        now = truncate(now, truncateToValue);
        return conversionService.convertRequired(now, property.getArgument());
    }

    @Nullable
    private Object truncate(@Nullable Object now, @Nullable ChronoUnit truncateToValue) {
        if (truncateToValue != null) {
            if (now instanceof OffsetDateTime offsetDateTime) {
                now = offsetDateTime.truncatedTo(truncateToValue);
            } else {
                Instant instant = conversionService.convertRequired(now, Instant.class);
                now = instant.truncatedTo(truncateToValue);
            }
        }
        return now;
    }

    @Nullable
    private Object computePropertyNow(@NonNull AnnotationMetadata annotationMetadata, boolean isUpdate, @Nullable Object now) {
        ChronoUnit truncateToValue;
        if (isUpdate) {
            truncateToValue = truncateToDateUpdated(annotationMetadata);
        } else {
            truncateToValue = truncateToDateCreated(annotationMetadata);
            if (truncateToValue == null) {
                truncateToValue = truncateToDateUpdated(annotationMetadata);
            }
        }
        return truncate(now, truncateToValue);
    }

    private @Nullable Object convertIfNeeded(@Nullable Object value, @NonNull Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        return conversionService.convert(value, targetType).orElse(null);
    }

    private boolean shouldSkipTimestamp(@NonNull AnnotationMetadata annotationMetadata,
                                        boolean hasDateCreated,
                                        boolean hasDateUpdated,
                                        boolean isUpdate,
                                        @Nullable Object currentValue) {
        if (currentValue == null) {
            return false;
        }
        if (hasDateCreated && annotationMetadata.booleanValue(DateCreated.class, DateCreated.SKIP_IF_PRESENT).orElse(false)) {
            return true;
        }
        return hasDateUpdated && !isUpdate && annotationMetadata.booleanValue(DateUpdated.class, DateUpdated.SKIP_IF_PRESENT).orElse(false);
    }

    private void autoTimestampIfNecessary(@NonNull EntityEventContext<Object> context, boolean isUpdate) {
        final RuntimePersistentProperty<Object>[] applicableProperties = getApplicableProperties(context);
        Object now = dateTimeProvider.getNow();
        // 1) Top-level properties
        AutoPopulateUtil.applyTopLevel(context, applicableProperties, prop -> {
            final AnnotationMetadata am = prop.getAnnotationMetadata();
            if (isUpdate && !am.booleanValue(AutoPopulated.class, AutoPopulated.UPDATABLE).orElse(true)) {
                return null;
            }
            final boolean hasDateCreated = am.hasAnnotation(DateCreated.class);
            final boolean hasDateUpdated = am.hasAnnotation(DateUpdated.class);
            Object current = prop.getProperty().get(context.getEntity());
            if (shouldSkipTimestamp(am, hasDateCreated, hasDateUpdated, isUpdate, current)) {
                return null;
            }
            Object propertyNow = computePropertyNow(am, isUpdate, now);
            return convertIfNeeded(propertyNow, prop.getType());
        });
        // 2) Embedded properties (recursive via util)
        AutoPopulateUtil.applyEmbedded(context, (embeddedPersistentProperty, current) -> {
            final AnnotationMetadata am = embeddedPersistentProperty.getAnnotationMetadata();
            final boolean hasDateCreated = am.hasAnnotation(DateCreated.class);
            final boolean hasDateUpdated = am.hasAnnotation(DateUpdated.class);
            if (!hasDateCreated && !hasDateUpdated) {
                return current;
            }
            if (isUpdate && !am.booleanValue(AutoPopulated.class, AutoPopulated.UPDATABLE).orElse(true)) {
                return current;
            }
            BeanProperty<Object, Object> prop = embeddedPersistentProperty.getProperty();
            if (!prop.hasSetterOrConstructorArgument()) {
                return current;
            }
            if (shouldSkipTimestamp(am, hasDateCreated, hasDateUpdated, isUpdate, prop.get(current))) {
                return current;
            }
            Object propertyNow = computePropertyNow(am, isUpdate, now);
            Class<?> propertyType = embeddedPersistentProperty.getType();
            Object newValue = convertIfNeeded(propertyNow, propertyType);
            if (prop.isReadOnly()) {
                return prop.withValue(current, newValue);
            } else {
                prop.set(current, newValue);
                return current;
            }
        });
    }

    @Nullable
    private ChronoUnit truncateToDateCreated(@NonNull AnnotationMetadata annotationMetadata) {
        return annotationMetadata.enumValue(DateCreated.class, "truncatedTo", ChronoUnit.class).filter(cu -> cu != ChronoUnit.FOREVER).orElse(null);
    }

    @Nullable
    private ChronoUnit truncateToDateUpdated(@NonNull AnnotationMetadata annotationMetadata) {
        return annotationMetadata.enumValue(DateUpdated.class, "truncatedTo", ChronoUnit.class).filter(cu -> cu != ChronoUnit.FOREVER).orElse(null);
    }
}
