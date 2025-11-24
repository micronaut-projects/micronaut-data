/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Internal utility to apply top-level auto-populated properties uniformly.
 *
 * Delegates assignment to EntityEventContext.setProperty which supports immutable entities.
 *
 * Centralizes the traversal of embedded associations and applies a provided
 * property population strategy. It returns the possibly new instance of the processed object,
 * which allows callers to reattach immutable objects at the root.
 */
@Internal
final class AutoPopulateUtil {

    private AutoPopulateUtil() {
    }

    /**
     * Apply supplied values to the given top-level properties.
     *
     * @param context    The entity event context
     * @param properties The top-level properties to consider
     * @param supplier   A supplier that returns the new value for a property or null to skip
     */
    static void applyTopLevel(EntityEventContext<Object> context,
                                     RuntimePersistentProperty<Object>[] properties,
                                     Function<RuntimePersistentProperty<Object>, Object> supplier) {
        for (RuntimePersistentProperty<Object> property : properties) {
            Object value = supplier.apply(property);
            if (value != null) {
                BeanProperty<Object, Object> beanProperty = property.getProperty();
                context.setProperty(beanProperty, value);
            }
        }
    }

    /**
     * Recursively traverse the provided embedded entity graph, applying the provided property setter at each level.
     *
     * @param embeddedEntity The runtime metadata for the current embedded entity
     * @param instance       The current instance to update (may be immutable)
     * @param propertySetter A function called for every persistent property at this level. It must return the (possibly new) instance.
     * @return The possibly new instance for this level after applying property updates and recursing into nested embeddeds
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object populateEmbedded(RuntimePersistentEntity<?> embeddedEntity,
                                          Object instance,
                                          BiFunction<RuntimePersistentProperty<Object>, Object, Object> propertySetter) {
        Object current = instance;

        // Apply property population at this level
        for (RuntimePersistentProperty<Object> p : (Collection<RuntimePersistentProperty<Object>>) (Collection) embeddedEntity.getPersistentProperties()) {
            current = propertySetter.apply(p, current);
        }

        // Recurse into nested embedded associations
        for (RuntimeAssociation<?> nested : embeddedEntity.getAssociations()) {
            if (!nested.isEmbedded()) {
                continue;
            }
            BeanProperty<Object, Object> ep = (BeanProperty<Object, Object>) nested.getProperty();
            Object child = ep.get(current);
            if (child == null) {
                child = nested.getAssociatedEntity().getIntrospection().instantiate();
            }
            Object updatedChild = populateEmbedded(nested.getAssociatedEntity(), child, propertySetter);
            if (ep.isReadOnly()) {
                current = ep.withValue(current, updatedChild);
            } else {
                ep.set(current, updatedChild);
            }
        }

        return current;
    }
}
