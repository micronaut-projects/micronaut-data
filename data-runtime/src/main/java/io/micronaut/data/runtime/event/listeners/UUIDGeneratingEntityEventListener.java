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
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

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
public class UUIDGeneratingEntityEventListener extends AutoPopulatedEntityEventListener {
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
        final RuntimePersistentProperty<Object>[] persistentProperties = getApplicableProperties(context.getPersistentEntity());
        for (RuntimePersistentProperty<Object> persistentProperty : persistentProperties) {
            final BeanProperty<Object, Object> property = persistentProperty.getProperty();
            context.setProperty(property, UUID.randomUUID());
        }
        return true;
    }
}
