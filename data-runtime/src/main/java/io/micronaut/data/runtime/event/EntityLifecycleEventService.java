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
package io.micronaut.data.runtime.event;

import io.micronaut.context.BeanContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.data.annotation.event.PostLoad;
import io.micronaut.data.annotation.event.PostPersist;
import io.micronaut.data.annotation.event.PostRemove;
import io.micronaut.data.annotation.event.PostUpdate;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreRemove;
import io.micronaut.data.annotation.event.PreUpdate;
import io.micronaut.data.event.EntityListener;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static io.micronaut.core.order.OrderUtil.getOrder;

/**
 * The service to trigger entity events.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
public class EntityLifecycleEventService {

    private final BeanContext beanContext;

    /**
     * The constructor.
     *
     * @param beanContext the bean context
     */
    public EntityLifecycleEventService(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * Triggers pre persist event.
     *
     * @param entity the entity instance
     */
    public void onPrePersist(@NotNull Object entity) {
        triggerEvent(PrePersist.class, entity);
    }

    /**
     * Triggers post persist event.
     *
     * @param entity the entity instance
     */
    public void onPostPersist(@NotNull Object entity) {
        triggerEvent(PostPersist.class, entity);
    }

    /**
     * Triggers pre persist event.
     *
     * @param entity the entity instance
     */
    public void onPreUpdate(@NotNull Object entity) {
        triggerEvent(PreUpdate.class, entity);
    }

    /**
     * Triggers post update event.
     *
     * @param entity the entity instance
     */
    public void onPostUpdate(@NotNull Object entity) {
        triggerEvent(PostUpdate.class, entity);
    }

    /**
     * Triggers pre remove event.
     *
     * @param entity the entity instance
     */
    public void onPreRemove(@NotNull Object entity) {
        triggerEvent(PreRemove.class, entity);
    }

    /**
     * Triggers post remove event.
     *
     * @param entity the entity instance
     */
    public void onPostRemove(@NotNull Object entity) {
        triggerEvent(PostRemove.class, entity);
    }

    /**
     * Triggers post load event.
     *
     * @param entity the entity instance
     */
    public void onPostLoad(@NotNull Object entity) {
        triggerEvent(PostLoad.class, entity);
    }

    private void triggerEvent(Class<? extends Annotation> annotation, @NotNull Object entity) {
        triggerDefaultEntityListeners(annotation, entity);
        // TODO: trigger @javax.persistence.EntityListeners listeners of the top level entity class -> actual entity class
        triggerInternalCallbackMethods(annotation, entity);
    }

    private void triggerInternalCallbackMethods(Class<? extends Annotation> annotation, @NotNull Object entity) {
        // TODO: JPA spec should trigger first methods from the top level entity class -> actual entity class
        for (BeanMethod beanMethod : BeanIntrospection.getIntrospection(entity.getClass()).getBeanMethods()) {
            if (beanMethod.isAnnotationPresent(annotation)) {
                beanMethod.invoke(entity);
            }
        }
    }

    private void triggerDefaultEntityListeners(Class<? extends Annotation> annotation, @NotNull Object entity) {
        // TODO: support @javax.persistence.ExcludeDefaultListeners
        Collection<BeanDefinition<EntityListener>> beanDefinitions = beanContext.getBeanDefinitions(EntityListener.class, Qualifiers.byTypeArguments(entity.getClass()));
        Collection<EntityEventHandler> handlers = new ArrayList<>(beanDefinitions.size());

        for (BeanDefinition<?> bd : beanDefinitions) {
            List<ExecutableMethod<Object, Object>> methods = new ArrayList<>(5);
            for (ExecutableMethod<?, ?> m : bd.getExecutableMethods()) {
                if (m.hasAnnotation(annotation)) {
                    methods.add((ExecutableMethod<Object, Object>) m);
                }
            }
            if (!methods.isEmpty()) {
                handlers.add(new EntityEventHandler((BeanDefinition<Object>) bd, methods, beanContext.getBean(bd)));
            }
        }

        handlers = handlers.stream().sorted((o1, o2) -> {
            int order1 = getOrder(o1.instance);
            int order2 = getOrder(o2.instance);
            return Integer.compare(order1, order2);
        }).collect(Collectors.toList());

        for (EntityEventHandler handler : handlers) {
            for (ExecutableMethod<Object, Object> executableMethod : handler.executableMethods) {
                executableMethod.invoke(handler.instance, entity);
            }
        }

        BeanDefinition<Object> beanDefinition = beanContext.findBeanDefinition((Class<Object>) entity.getClass()).orElse(null);
        if (beanDefinition == null) {
            return;
        }
        for (ExecutableMethod<Object, ?> m : beanDefinition.getExecutableMethods()) {
            if (m.hasAnnotation(annotation)) {
                m.invoke(entity);
            }
        }
    }

    private static class EntityEventHandler {
        final BeanDefinition<Object> beanDefinition;
        final List<ExecutableMethod<Object, Object>> executableMethods;
        final Object instance;

        EntityEventHandler(BeanDefinition<Object> beanDefinition,
                           List<ExecutableMethod<Object, Object>> executableMethods,
                           Object instance) {
            this.beanDefinition = beanDefinition;
            this.executableMethods = executableMethods;
            this.instance = instance;
        }
    }

}
