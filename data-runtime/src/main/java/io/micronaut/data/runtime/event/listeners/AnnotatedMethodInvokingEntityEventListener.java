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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.data.annotation.event.*;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.event.PersistenceEventException;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Collection;

/**
 * Event listener that allows invoking methods defined on entities and annotated with an event annotation such as {@link io.micronaut.data.annotation.event.PrePersist}.
 *
 * @author graemerocher
 * @since 2.3.0
 */
@Singleton
public class AnnotatedMethodInvokingEntityEventListener implements EntityEventListener<Object> {

    @Override
    public boolean supports(RuntimePersistentEntity<Object> entity, Class<? extends Annotation> eventType) {
        final BeanIntrospection<Object> introspection = entity.getIntrospection();
        final Collection<BeanMethod<Object, Object>> beanMethods = introspection.getBeanMethods();
        return beanMethods.stream()
                .anyMatch(beanMethod -> beanMethod.isAnnotationPresent(eventType));
    }

    @Override
    public boolean prePersist(@NonNull EntityEventContext<Object> context) {
        triggerEvent(context, PrePersist.class.getName());
        return true;
    }

    @Override
    public void postPersist(@NonNull EntityEventContext<Object> context) {
        triggerEvent(context, PostPersist.class.getName());
    }

    @Override
    public void postLoad(@NonNull EntityEventContext<Object> context) {
        triggerEvent(context, PostLoad.class.getName());
    }

    @Override
    public boolean preRemove(@NonNull EntityEventContext<Object> context) {
        triggerEvent(context, PreRemove.class.getName());
        return true;
    }

    @Override
    public void postRemove(@NonNull EntityEventContext<Object> context) {
        triggerEvent(context, PostRemove.class.getName());
    }

    @Override
    public boolean preUpdate(@NonNull EntityEventContext<Object> context) {
        triggerEvent(context, PreUpdate.class.getName());
        return true;
    }

    @Override
    public void postUpdate(@NonNull EntityEventContext<Object> context) {
        triggerEvent(context, PostUpdate.class.getName());
    }

    private void triggerEvent(@NonNull EntityEventContext<Object> context, String annotationName) {
        if (context.supportsEventSystem()) {
            final RuntimePersistentEntity<Object> persistentEntity = context.getPersistentEntity();
            persistentEntity.getIntrospection().getBeanMethods()
                    .forEach(beanMethod -> {
                        if (beanMethod.getAnnotationMetadata().hasAnnotation(annotationName)) {
                            try {
                                beanMethod.invoke(context.getEntity());
                            } catch (Exception e) {
                                throw new PersistenceEventException("Error invoking persistence event method [" + beanMethod.getName() + "] on entity [" + persistentEntity.getName() + "]: " + e.getMessage(), e);
                            }
                        }
                    });
        }
    }
}
