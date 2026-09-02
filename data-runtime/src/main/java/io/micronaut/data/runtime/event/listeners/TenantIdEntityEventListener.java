/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.model.runtime.PropertyAutoPopulator;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.event.UpsertEntityEventListener;
import io.micronaut.data.runtime.multitenancy.TenantResolver;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Predicate;

/**
 * An event listener that handles {@link TenantId}.
 *
 * @author Denis Stepanov
 * @since 4.8.0
 */
@Requires(beans = TenantResolver.class)
@Singleton
public class TenantIdEntityEventListener extends AutoPopulatedEntityEventListener implements PropertyAutoPopulator<TenantId>, UpsertEntityEventListener<Object> {

    private final TenantResolver tenantResolver;
    private final DataConversionService conversionService;

    /**
     * Default constructor.
     *
     * @param conversionService The conversion service
     * @param tenantResolver    The tenant resolver
     */
    public TenantIdEntityEventListener(TenantResolver tenantResolver, DataConversionService conversionService) {
        this.tenantResolver = tenantResolver;
        this.conversionService = conversionService;
    }

    @NonNull
    @Override
    protected List<Class<? extends Annotation>> getEventTypes() {
        return List.of(PrePersist.class);
    }

    @NonNull
    @Override
    protected Predicate<RuntimePersistentProperty<Object>> getPropertyPredicate() {
        return (prop) -> {
            final AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
            return annotationMetadata.hasStereotype(TenantId.class);
        };
    }

    @Override
    public boolean prePersist(@NonNull EntityEventContext<Object> context) {
        populateTenantId(context);
        return true;
    }

    @Override
    public void prepareUpsert(@NonNull EntityEventContext<Object> context) {
        populateTenantId(context);
    }

    private void populateTenantId(EntityEventContext<Object> context) {
        for (RuntimePersistentProperty<Object> property : getApplicableProperties(context)) {
            if (property.getAnnotationMetadata().hasStereotype(TenantId.class)) {
                if (property.getProperty().get(context.getEntity()) != null) {
                    // Skip existing value
                    return;
                }
                Argument<Object> argument = property.getArgument();
                Object newValue = tenantResolver.resolveTenantIdentifier();
                if (!argument.isInstance(newValue)) {
                    newValue = conversionService.convert(newValue, argument.getType());
                }
                context.setProperty(property.getProperty(), newValue);
                break;
            }
        }
    }

    @Override
    @NonNull
    public Object populate(RuntimePersistentProperty<?> property, @Nullable Object previousValue) {
        return conversionService.convertRequired(tenantResolver.resolveTenantIdentifier(), property.getArgument());
    }

}
