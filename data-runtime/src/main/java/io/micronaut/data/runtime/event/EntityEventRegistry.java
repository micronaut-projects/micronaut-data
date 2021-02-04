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
package io.micronaut.data.runtime.event;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.event.*;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.event.PersistenceEventException;
import io.micronaut.data.event.QueryEventContext;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.inject.BeanDefinition;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Primary implementation of the {@link EntityEventListener} interface that aggregates all other listeners.
 *
 * @author graemerocher
 * @since 2.3.0
 */
@Singleton
@Primary
public class EntityEventRegistry implements EntityEventListener<Object> {
    public static final List<Class<? extends Annotation>> EVENT_TYPES = Arrays.asList(
            PostLoad.class,
            PostPersist.class,
            PostRemove.class,
            PostUpdate.class,
            PrePersist.class,
            PreRemove.class,
            PreUpdate.class
    );
    private final Collection<BeanDefinition<EntityEventListener>> allEventListeners;
    private final Map<RuntimePersistentEntity<Object>, Map<Class<? extends
            Annotation>, EntityEventListener<Object>>> entityToEventListeners = new ConcurrentHashMap<>(50);
    private final BeanContext beanContext;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     */
    public EntityEventRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
        //noinspection RedundantCast
        this.allEventListeners = beanContext.getBeanDefinitions(EntityEventListener.class)
                .stream().filter(bd -> ((Class) bd.getBeanType()) != (Class) getClass())
                .collect(Collectors.toList());
    }

    @Override
    public boolean supports(RuntimePersistentEntity<Object> entity, Class<? extends Annotation> eventType) {
        Map<Class<? extends Annotation>, EntityEventListener<Object>> listeners = getListeners(entity);
        return listeners.containsKey(eventType);
    }

    @Override
    public boolean prePersist(@NonNull EntityEventContext<Object> context) {
        try {
            final EntityEventListener<Object> target = getListeners(context.getPersistentEntity()).get(PrePersist.class);
            if (target != null) {
                return target.prePersist(context);
            }
        } catch (Exception e) {
            throw new PersistenceEventException("An error occurred invoking pre-persist event listeners: " + e.getMessage(), e);
        }
        return true;
    }

    @Override
    public void postPersist(@NonNull EntityEventContext<Object> context) {
        try {
            final EntityEventListener<Object> target = getListeners(context.getPersistentEntity()).get(PostPersist.class);
            if (target != null) {
                target.postPersist(context);
            }
        } catch (Exception e) {
            throw new PersistenceEventException("An error occurred invoking post-persist event listeners: " + e.getMessage(), e);
        }
    }

    @Override
    public void postLoad(@NonNull EntityEventContext<Object> context) {
        try {
            final EntityEventListener<Object> target = getListeners(context.getPersistentEntity()).get(PostLoad.class);
            if (target != null) {
                target.postLoad(context);
            }
        } catch (Exception e) {
            throw new PersistenceEventException("An error occurred invoking post-load event listeners: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean preRemove(@NonNull EntityEventContext<Object> context) {
        try {
            final EntityEventListener<Object> target = getListeners(context.getPersistentEntity()).get(PreRemove.class);
            if (target != null) {
                return target.preRemove(context);
            }
            return true;
        } catch (Exception e) {
            throw new PersistenceEventException("An error occurred invoking pre-remove event listeners: " + e.getMessage(), e);
        }
    }

    @Override
    public void postRemove(@NonNull EntityEventContext<Object> context) {
        try {
            final EntityEventListener<Object> target = getListeners(context.getPersistentEntity()).get(PostRemove.class);
            if (target != null) {
                target.postRemove(context);
            }
        } catch (Exception e) {
            throw new PersistenceEventException("An error occurred invoking post-remove event listeners: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean preUpdate(@NonNull EntityEventContext<Object> context) {
        try {
            final EntityEventListener<Object> target = getListeners(context.getPersistentEntity()).get(PreUpdate.class);
            if (target != null) {
                return target.preUpdate(context);
            }
            return true;
        } catch (Exception e) {
            throw new PersistenceEventException("An error occurred invoking pre-update event listeners: " + e.getMessage(), e);
        }
    }

    @Override
    public void postUpdate(@NonNull EntityEventContext<Object> context) {
        try {
            final EntityEventListener<Object> target = getListeners(context.getPersistentEntity()).get(PostUpdate.class);
            if (target != null) {
                target.postUpdate(context);
            }
        } catch (Exception e) {
            throw new PersistenceEventException("An error occurred invoking post-update event listeners: " + e.getMessage(), e);
        }
    }

    @NonNull
    private Map<Class<? extends Annotation>, EntityEventListener<Object>> getListeners(RuntimePersistentEntity<Object> entity) {
        Map<Class<? extends Annotation>, EntityEventListener<Object>> listeners = entityToEventListeners.get(entity);
        if (listeners == null) {
            listeners = initListeners(entity);
            entityToEventListeners.put(entity, listeners);
        }
        return listeners;
    }

    @NonNull
    private Map<Class<? extends Annotation>, EntityEventListener<Object>> initListeners(RuntimePersistentEntity<Object> entity) {
        Map<Class<? extends Annotation>, Collection<EntityEventListener<Object>>> listeners = new HashMap<>(8);
        for (BeanDefinition<EntityEventListener> beanDefinition : allEventListeners) {
            final List<Argument<?>> typeArguments = beanDefinition.getTypeArguments(EntityEventListener.class);
            if (isApplicableListener(entity, typeArguments)) {
                @SuppressWarnings("unchecked")
                final EntityEventListener<Object> eventListener = beanContext.getBean(beanDefinition);
                for (Class<? extends Annotation> et : EVENT_TYPES) {
                    if (eventListener.supports(entity, et)) {
                        final Collection<EntityEventListener<Object>> eventListeners =
                                listeners.computeIfAbsent(et, (t) -> new ArrayList<>(5));
                        eventListeners.add(eventListener);
                    }
                }
            }
        }
        Map<Class<? extends Annotation>, EntityEventListener<Object>> finalListeners;
        if (listeners.isEmpty()) {
            finalListeners = Collections.emptyMap();
        } else {
            finalListeners = listeners.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    (entry) -> {
                        final Collection<EntityEventListener<Object>> v = entry.getValue();
                        if (v.isEmpty()) {
                            return EntityEventListener.NOOP;
                        } else if (v.size() == 1) {
                            return v.iterator().next();
                        } else {
                            return new CompositeEventListener(v);
                        }
                    }
            ));
        }
        return finalListeners;
    }

    private boolean isApplicableListener(RuntimePersistentEntity<Object> entity, List<Argument<?>> typeArguments) {
        return typeArguments.isEmpty() || typeArguments.get(0).getType().isAssignableFrom(entity.getIntrospection().getBeanType());
    }

    private static final class CompositeEventListener implements EntityEventListener<Object> {
        private final EntityEventListener<Object>[] listenerArray;

        public CompositeEventListener(Collection<EntityEventListener<Object>> listeners) {
            //noinspection unchecked
            this.listenerArray = listeners.stream().sorted(OrderUtil.COMPARATOR).toArray(EntityEventListener[]::new);
        }

        @Override
        public boolean supports(RuntimePersistentEntity<Object> entity, Class<? extends Annotation> eventType) {
            for (EntityEventListener<Object> listener : listenerArray) {
                if (listener.supports(entity, eventType)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean prePersist(@NonNull EntityEventContext<Object> context) {
            for (EntityEventListener<Object> listener : listenerArray) {
                if (!listener.prePersist(context)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void postPersist(@NonNull EntityEventContext<Object> context) {
            for (EntityEventListener<Object> listener : listenerArray) {
                listener.postPersist(context);
            }
        }

        @Override
        public void postLoad(@NonNull EntityEventContext<Object> context) {
            for (EntityEventListener<Object> listener : listenerArray) {
                listener.postLoad(context);
            }
        }

        @Override
        public boolean preRemove(@NonNull EntityEventContext<Object> context) {
            for (EntityEventListener<Object> listener : listenerArray) {
                if (!listener.preRemove(context)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void postRemove(@NonNull EntityEventContext<Object> context) {
            for (EntityEventListener<Object> listener : listenerArray) {
                listener.postRemove(context);
            }
        }

        @Override
        public boolean preUpdate(@NonNull EntityEventContext<Object> context) {
            for (EntityEventListener<Object> listener : listenerArray) {
                if (!listener.preUpdate(context)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean preQuery(@NonNull QueryEventContext<Object> context) {
            for (EntityEventListener<Object> listener : listenerArray) {
                if (!listener.preQuery(context)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void postUpdate(@NonNull EntityEventContext<Object> context) {
            for (EntityEventListener<Object> listener : listenerArray) {
                listener.postUpdate(context);
            }
        }
    }
}
