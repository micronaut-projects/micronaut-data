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

import org.jspecify.annotations.NonNull;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.event.UpsertEntityEventListener;

import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Supports generating UUIDs.
 *
 * @author graemerocher
 * @since 2.3.0
 */
@Singleton
public class UUIDGeneratingEntityEventListener extends AutoPopulatedEntityEventListener implements UpsertEntityEventListener<Object> {

    private static final Predicate<RuntimePersistentProperty<Object>> UUID_PREDICATE = p -> p.getType() == UUID.class;

    @NonNull
    @Override
    protected final List<Class<? extends Annotation>> getEventTypes() {
        return Collections.singletonList(PrePersist.class);
    }

    @NonNull
    @Override
    protected Predicate<RuntimePersistentProperty<Object>> getPropertyPredicate() {
        return UUID_PREDICATE;
    }

    @Override
    public boolean prePersist(@NonNull EntityEventContext<Object> context) {
        populateUuids(context);
        return true;
    }

    @Override
    public void prepareUpsert(@NonNull EntityEventContext<Object> context) {
        populateUuids(context);
    }

    private void populateUuids(EntityEventContext<Object> context) {
        // 1) Top-level @AutoPopulated UUID properties resolved by getApplicableProperties.
        final RuntimePersistentProperty<Object>[] persistentProperties = getApplicableProperties(context);
        final Object entity = context.getEntity();
        AutoPopulateUtil.applyTopLevel(context, persistentProperties, property ->
            shouldSkipPopulation(property, entity) ? null : UUID.randomUUID()
        );

        // 2) Embedded properties (recursive via util)
        AutoPopulateUtil.applyEmbedded(context, (embeddedPersistentProperty, current) -> {
            if (embeddedPersistentProperty.getType() != UUID.class) {
                return current;
            }
            if (!embeddedPersistentProperty.isAutoPopulated() && !embeddedPersistentProperty.getAnnotationMetadata().hasStereotype(AutoPopulated.class)) {
                return current;
            }
            BeanProperty<Object, Object> prop = embeddedPersistentProperty.getProperty();
            if (!prop.hasSetterOrConstructorArgument()) {
                return current;
            }
            boolean skipIfPresent = embeddedPersistentProperty.getAnnotationMetadata().booleanValue(AutoPopulated.class, AutoPopulated.SKIP_IF_PRESENT).orElse(false);
            if (skipIfPresent) {
                Object existing = prop.get(current);
                if (existing != null) {
                    return current; // skip
                }
            }
            UUID value = UUID.randomUUID();
            if (prop.isReadOnly()) {
                return prop.withValue(current, value);
            } else {
                prop.set(current, value);
                return current;
            }
        });

    }

    private static boolean shouldSkipPopulation(RuntimePersistentProperty<Object> property, Object entity) {
        return property.getAnnotationMetadata().booleanValue(AutoPopulated.class, AutoPopulated.SKIP_IF_PRESENT).orElse(false)
            && property.getProperty().get(entity) != null;
    }
}
