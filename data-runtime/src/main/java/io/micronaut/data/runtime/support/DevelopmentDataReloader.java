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
package io.micronaut.data.runtime.support;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.watch.BeanDefinitionChange;
import io.micronaut.context.watch.ExecutableMethodChange;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.event.EntityEventMapping;
import io.micronaut.data.connection.interceptor.ConnectableInterceptor;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.event.EntityEventRegistry;
import io.micronaut.data.runtime.intercept.DataInterceptorResolver;
import io.micronaut.data.runtime.support.convert.AttributeConverterProvider;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.interceptor.TransactionalEventInterceptor;
import io.micronaut.transaction.interceptor.TransactionalInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the beans of Micronaut Data in step with the application's entities and repositories in development mode.
 * It exists only in development mode, so nothing of it is on the path of a query or a repository invocation.
 *
 * <p>Those beans derive state from the entity and repository classes and remember it: the entity registry holds a
 * {@code RuntimePersistentEntity} per entity class, the repository operations build a query builder per repository
 * type as they are created, the interceptor resolver and the interceptors it creates cache stored queries per
 * repository method, and the entity event registry reads the event listeners as it is created. Rather than evict
 * each of those caches, the reloader recreates the beans that hold them through
 * {@link WatchableBeanContext#recreate(Object)}; the beans that received them, such as the repositories and the
 * application beans that use them, are destroyed with them, as the dependency graph records, and are created again
 * on top of the new ones when they are next asked for.</p>
 *
 * <ul>
 *     <li>A class change applied in place that retires a classloader recreates every one of those beans, together
 *     with the converter providers and the transaction and connection interceptors, which cache by classes and
 *     methods of the retired generation.</li>
 *     <li>A class change applied in place that redefines an entity, an embeddable, a repository or an entity event
 *     listener recreates the entity registry, the repository operations, the interceptor resolver, the entity
 *     event registry and the entity event listeners, which cache by entity.</li>
 *     <li>A repository definition added or removed, such as one registered at runtime, recreates the repository
 *     operations and the interceptor resolver: the operations know the repositories they serve from when they
 *     were created.</li>
 *     <li>An entity event listener definition, or a method mapped to an entity event, added or removed recreates
 *     the entity event registry.</li>
 * </ul>
 *
 * <p>A change that restarts the application is ignored: the new context creates all of them anew. A context that
 * does not track bean dependencies recreates nothing, and the beans are kept, rather than replaced under the beans
 * that received them.</p>
 *
 * <p>It holds the context only, never a data bean: a bean that received one is a dependent of it, which recreating
 * it would destroy along with its watches.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevelopmentDataReloader {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentDataReloader.class);

    /**
     * The beans that hold state derived from the entity and repository classes.
     */
    private static final List<Class<?>> MODEL_BEANS = List.of(
        RuntimeEntityRegistry.class,
        RepositoryOperations.class,
        DataInterceptorResolver.class,
        EntityEventRegistry.class,
        // the listeners themselves cache by entity, such as the auto-populating and the Jakarta Data ones
        EntityEventListener.class
    );

    /**
     * The beans that also hold classes or methods of any application class, not only of entities and repositories.
     */
    private static final List<Class<?>> CLASS_KEYED_BEANS = List.of(
        AttributeConverterProvider.class,
        TransactionalInterceptor.class,
        TransactionalEventInterceptor.class,
        ConnectableInterceptor.class
    );

    /**
     * The beans that hold what was known of the repositories as they were created.
     */
    private static final List<Class<?>> REPOSITORY_BEANS = List.of(
        RepositoryOperations.class,
        DataInterceptorResolver.class
    );

    private final BeanContext beanContext;

    /**
     * @param beanContext The context, watched when it can be
     */
    DevelopmentDataReloader(BeanContext beanContext) {
        this.beanContext = beanContext;
        if (beanContext instanceof WatchableBeanContext watchable) {
            watchable.watchClassChanges(this::onClassChange);
            watchable.watchDefinitions(Object.class, Qualifiers.byStereotype(Repository.class), this::onRepositoryDefinitions);
            watchable.watchDefinitions(EntityEventListener.class, null, this::onEntityEventListenerDefinitions);
            watchable.watchMethods(EntityEventMapping.class, this::onEntityEventMethods);
        }
    }

    private void onClassChange(ClassChangeEvent change) {
        if (change.strategy() == ReloadStrategy.RESTART) {
            return;
        }
        if (!change.retiredLoaders().isEmpty()) {
            List<Class<?>> types = new ArrayList<>(MODEL_BEANS);
            types.addAll(CLASS_KEYED_BEANS);
            recreate(types, "a reload retired a classloader");
            return;
        }
        for (ClassChange classChange : change.changes()) {
            String className = classChange.className();
            if (isDataClass(className, change.newLoader())) {
                recreate(MODEL_BEANS, className + " changed");
                return;
            }
        }
    }

    private void onRepositoryDefinitions(BeanDefinitionChange<Object> change) {
        if (!change.initial() && (!change.added().isEmpty() || !change.removed().isEmpty())) {
            recreate(REPOSITORY_BEANS, "the repository definitions changed");
        }
    }

    @SuppressWarnings("rawtypes")
    private void onEntityEventListenerDefinitions(BeanDefinitionChange<EntityEventListener> change) {
        if (!change.initial() && (!change.added().isEmpty() || !change.removed().isEmpty())) {
            recreate(List.of(EntityEventRegistry.class), "the entity event listener definitions changed");
        }
    }

    private void onEntityEventMethods(ExecutableMethodChange<EntityEventMapping> change) {
        if (!change.initial() && (!change.added().isEmpty() || !change.removed().isEmpty())) {
            recreate(List.of(EntityEventRegistry.class), "the entity event methods changed");
        }
    }

    /**
     * Whether a changed class, or the class a generated class was generated for, is an entity, an embeddable, a
     * repository or an entity event listener.
     *
     * @param className The changed class
     * @param loader The loader of the new generation
     * @return Whether the beans of Micronaut Data may hold what it replaced
     */
    private boolean isDataClass(String className, ClassLoader loader) {
        String origin = originOf(className);
        return isEntity(className, loader)
            || (origin != null && isEntity(origin, loader))
            || isDataBean(className)
            || (origin != null && isDataBean(origin));
    }

    /**
     * The class a generated class, such as {@code $Book$Introspection} or
     * {@code $BookRepository$Intercepted$Definition}, was generated for.
     *
     * @param className A class name
     * @return The class it was generated for, or null when it is not a generated class
     */
    @Nullable
    private static String originOf(String className) {
        int lastDot = className.lastIndexOf('.');
        String simpleName = className.substring(lastDot + 1);
        if (simpleName.length() < 2 || simpleName.charAt(0) != '$') {
            return null;
        }
        int end = simpleName.indexOf('$', 1);
        String origin = end == -1 ? simpleName.substring(1) : simpleName.substring(1, end);
        return className.substring(0, lastDot + 1) + origin;
    }

    private static boolean isEntity(String className, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            Optional<? extends BeanIntrospection<?>> introspection = BeanIntrospector.forClassLoader(loader).findIntrospection(type);
            return introspection.isPresent()
                && (introspection.get().hasStereotype(MappedEntity.class) || introspection.get().hasStereotype(Embeddable.class));
        } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
            // removed, or not loadable on its own: no entity of the new generation is built from it
            return false;
        }
    }

    /**
     * Whether a repository or an entity event listener of that class is defined. Definitions are matched by the
     * name of their bean type, so that no class is loaded and none of a previous generation is kept.
     *
     * @param className The changed class
     * @return Whether it is a repository or an entity event listener
     */
    private boolean isDataBean(String className) {
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(Repository.class))) {
            if (defines(definition, className)) {
                return true;
            }
        }
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(EntityEventListener.class)) {
            if (defines(definition, className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a definition is of the given class: its bean type, or a type it exposes, such as the repository
     * interface that an introduction advice implements.
     *
     * @param definition The definition
     * @param className The class name
     * @return Whether the definition is of that class
     */
    private static boolean defines(BeanDefinition<?> definition, String className) {
        if (definition.getBeanType().getName().equals(className)) {
            return true;
        }
        for (Class<?> exposed : definition.getExposedTypes()) {
            if (exposed.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recreates the singletons of the given types the context holds, and the beans that received them. Nothing is
     * created that was not created already.
     *
     * @param types The bean types
     * @param reason Why, for the log
     */
    private void recreate(List<Class<?>> types, String reason) {
        if (!(beanContext instanceof WatchableBeanContext context)) {
            return;
        }
        // taken first: recreating one destroys the beans that received it, as the graph records them
        List<Object> beans = new ArrayList<>();
        for (Class<?> type : types) {
            for (BeanRegistration<?> registration : beanContext.getActiveBeanRegistrations(type)) {
                add(beans, registration.bean());
            }
        }
        if (beans.isEmpty()) {
            return;
        }
        LOG.debug("Recreating {} Micronaut Data bean(s): {}", beans.size(), reason);
        for (Object bean : beans) {
            // false for a bean destroyed with one recreated before it, and for all of them in a context that does
            // not track bean dependencies: they are kept
            context.recreate(bean);
        }
    }

    @SuppressWarnings("ReferenceEquality") // beans are the same bean by identity
    private static void add(List<Object> beans, Object bean) {
        for (Object taken : beans) {
            if (taken == bean) {
                return;
            }
        }
        beans.add(bean);
    }
}
