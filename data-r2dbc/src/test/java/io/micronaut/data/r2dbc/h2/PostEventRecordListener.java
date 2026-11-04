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
package io.micronaut.data.r2dbc.h2;

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.annotation.event.PostPersist;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replaces the immutable {@link PostEventRecord} after it is persisted and records the propagated context visible to the listener.
 */
@Singleton
public class PostEventRecordListener implements EntityEventListener<PostEventRecord> {

    public static final String STATUS = "persisted";

    private final List<String> prePersistContexts = new CopyOnWriteArrayList<>();
    private final List<String> postPersistContexts = new CopyOnWriteArrayList<>();

    @Override
    public boolean supports(RuntimePersistentEntity<PostEventRecord> entity, Class<? extends Annotation> eventType) {
        return eventType == PrePersist.class || eventType == PostPersist.class;
    }

    @Override
    public boolean prePersist(EntityEventContext<PostEventRecord> context) {
        prePersistContexts.add(currentContextValue());
        return true;
    }

    @Override
    public void postPersist(EntityEventContext<PostEventRecord> context) {
        postPersistContexts.add(currentContextValue());
        context.setProperty(context.getPersistentEntity().getIntrospection().getRequiredProperty("status", String.class), STATUS);
    }

    public List<String> getPrePersistContexts() {
        return prePersistContexts;
    }

    public List<String> getPostPersistContexts() {
        return postPersistContexts;
    }

    public void clear() {
        prePersistContexts.clear();
        postPersistContexts.clear();
    }

    private static String currentContextValue() {
        return PropagatedContext.getOrEmpty().find(TestContextElement.class).map(TestContextElement::value).orElse("none");
    }

    /**
     * The propagated context element.
     *
     * @param value The value
     */
    public record TestContextElement(String value) implements PropagatedContextElement {
    }
}
