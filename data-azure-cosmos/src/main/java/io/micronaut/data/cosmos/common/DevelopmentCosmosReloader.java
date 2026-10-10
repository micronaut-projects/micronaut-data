/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.cosmos.common;

import io.micronaut.context.BeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.watch.ClassChangeWatcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the Cosmos entities that {@link CosmosEntity} holds, in a static map keyed by persistent entity, in step with
 * the application in development mode. It exists only in development mode: the map, and the operations that read
 * it, are unchanged.
 *
 * <p>Persistent entities are equal by entity name, so the map holds one Cosmos entity per entity class name, whatever
 * generation of the class it was created for, and the database initializer of a generation only creates the entries
 * that are missing. Without this reloader the next generation would be given the Cosmos entities of the first one,
 * whose partition key or container settings may have changed since, and the first generation, the key of those
 * entries, would stay reachable for the life of the process.</p>
 *
 * <ul>
 *     <li>As the context stops, on a restart, it forgets the Cosmos entities of the entity classes its own classloader
 *     defined, the generation's, so that the next context creates its own.</li>
 *     <li>A class change applied in place that retires a classloader forgets the Cosmos entities of the persistent
 *     entities whose class that loader defined.</li>
 *     <li>A class change applied in place, after which the data beans recreated the entity registry, and with it, as
 *     a bean that received the registry, the database initializer, forgets the Cosmos entities of the entity classes
 *     its classloader defined and initializes the database again, as the application did as it started: the persistent
 *     entities of the new registry get Cosmos entities of their own, and the containers of entities added in place are
 *     created.</li>
 * </ul>
 *
 * <p>An entity class of a library, which the classloader of the context did not define, may be used by another
 * context at the same time: its Cosmos entity is kept, as it is outside development mode. Such a class does not
 * change with the application's generations.</p>
 *
 * <p>It holds the context only, never an entity nor a data bean.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevelopmentCosmosReloader {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentCosmosReloader.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext The context, watched when it can be
     */
    DevelopmentCosmosReloader(BeanContext beanContext) {
        this.beanContext = beanContext;
        if (beanContext instanceof WatchableBeanContext watchable) {
            watchable.classChanges().watch(new CosmosWatcher());
        }
    }

    /**
     * Forgets the Cosmos entities of the entity classes the classloader of this context defined, as it stops.
     */
    @PreDestroy
    void close() {
        ClassLoader loader = beanContext.getClassLoader();
        CosmosEntity.forget(entity -> isDefinedBy(entity, loader));
    }

    private void onClassChange(ClassChangeEvent change) {
        if (change.strategy() == ReloadStrategy.RESTART) {
            // the context stops, and forgets what it created as it does
            return;
        }
        if (!change.retiredLoaders().isEmpty()) {
            CosmosEntity.forget(entity -> change.isStaleType(entity.getIntrospection().getBeanType()));
        }
        reinitialize();
    }

    /**
     * Initializes the database again when the initializer was destroyed with the entity registry it received.
     */
    private void reinitialize() {
        if (!beanContext.getActiveBeanRegistrations(CosmosDatabaseInitializer.class).isEmpty()
            || !beanContext.containsBean(CosmosDatabaseInitializer.class)) {
            return;
        }
        LOG.debug("Initializing the Cosmos database again: the entity registry was recreated");
        // first: the initializer only creates the entries that are missing, by entity name
        ClassLoader loader = beanContext.getClassLoader();
        CosmosEntity.forget(entity -> isDefinedBy(entity, loader));
        beanContext.getBean(CosmosDatabaseInitializer.class);
    }

    /**
     * Whether the class of a persistent entity was defined by a loader, the loader of a generation.
     */
    @SuppressWarnings("ReferenceEquality") // a generation is its loader
    private static boolean isDefinedBy(RuntimePersistentEntity<?> entity, ClassLoader loader) {
        return entity.getIntrospection().getBeanType().getClassLoader() == loader;
    }

    /**
     * Follows the class changes after the data beans are recreated, so that the database is initialized with the
     * entity registry of the new generation.
     */
    private final class CosmosWatcher implements ClassChangeWatcher, Ordered {
        @Override
        public void onChange(ClassChangeEvent change) {
            onClassChange(change);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
