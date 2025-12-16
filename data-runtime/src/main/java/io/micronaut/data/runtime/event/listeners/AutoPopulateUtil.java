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
import org.jspecify.annotations.NonNull;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(AutoPopulateUtil.class);

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
     * Traverse all embedded associations at the root level of the given context, instantiate missing
     * embedded instances if necessary, delegate recursive population to {@link #populateEmbedded(RuntimePersistentEntity, Object, BiFunction)},
     * and reattach the resulting instance via {@link EntityEventContext#setProperty(BeanProperty, Object)}.
     *
     * This method centralizes the boilerplate to:
     * - find embedded associations of the root entity
     * - lazily instantiate null embedded instances
     * - apply the provided propertySetter within the embedded graph (recursively)
     * - support immutable entities by reattaching the updated instance through the context
     *
     * @param context        The entity event context providing the root entity and its metadata
     * @param propertySetter A function applied to each persistent property encountered during recursion.
     *                       It receives the current persistent property and the current instance for that level,
     *                       and must return the (possibly new) instance for that level.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void applyEmbedded(@NonNull EntityEventContext<Object> context,
                              BiFunction<RuntimePersistentProperty<Object>, Object, Object> propertySetter) {
        final RuntimePersistentEntity<Object> persistentEntity = context.getPersistentEntity();
        final Object rootEntity = context.getEntity();
        for (RuntimeAssociation<?> association : persistentEntity.getAssociations()) {
            if (association.isEmbedded()) {
                @SuppressWarnings("unchecked")
                BeanProperty<Object, Object> embeddedProperty = (BeanProperty<Object, Object>) association.getProperty();
                Object embedded = embeddedProperty.get(rootEntity);
                if (embedded == null) {
                    try {
                        embedded = association.getAssociatedEntity().getIntrospection().instantiate();
                    } catch (Exception e) {
                        LOG.warn("Unable to instantiate embedded property: {}", embeddedProperty.getName(), e);
                        continue;
                    }
                }
                Object updated = populateEmbedded(association.getAssociatedEntity(), embedded, propertySetter);
                context.setProperty(embeddedProperty, updated);
            }
        }
    }

    /**
     * Recursively traverse the provided embedded entity graph, applying the provided property setter at each level.
     *
     * @param embeddedEntity The runtime metadata for the current embedded entity
     * @param instance       The current instance to update (maybe immutable)
     * @param propertySetter A function called for every persistent property at this level. It must return the (possibly new) instance.
     * @return The possibly new instance for this level after applying property updates and recursing into nested embeddeds
     */
    static Object populateEmbedded(@NonNull RuntimePersistentEntity<?> embeddedEntity,
                                   @NonNull Object instance,
                                   BiFunction<RuntimePersistentProperty<Object>, Object, Object> propertySetter) {
        Object current = instance;

        // Apply property population at this level
        for (RuntimePersistentProperty<Object> p : (Collection<RuntimePersistentProperty<Object>>) (Collection) embeddedEntity.getPersistentProperties()) {
            current = propertySetter.apply(p, current);
        }

        // Recurse into nested embedded associations
        for (RuntimeAssociation<?> nested : embeddedEntity.getAssociations()) {
            if (nested.isEmbedded()) {
                BeanProperty<Object, Object> ep = (BeanProperty<Object, Object>) nested.getProperty();
                Object child = ep.get(current);
                if (child == null) {
                    try {
                        child = nested.getAssociatedEntity().getIntrospection().instantiate();
                    } catch (Exception e) {
                        LOG.warn("Unable to instantiate embedded property: {}", ep.getName(), e);
                        continue;
                    }
                }
                Object updatedChild = populateEmbedded(nested.getAssociatedEntity(), child, propertySetter);
                if (ep.isReadOnly()) {
                    current = ep.withValue(current, updatedChild);
                } else {
                    ep.set(current, updatedChild);
                }
            }
        }

        return current;
    }
}
