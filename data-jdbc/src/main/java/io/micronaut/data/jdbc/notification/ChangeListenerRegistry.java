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
package io.micronaut.data.jdbc.notification;

import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects methods annotated with {@link ChangeListener} and groups them by datasource.
 *
 * <p>Micronaut discovers the methods through {@link ExecutableMethodProcessor}. The registry
 * records each method's bean definition and entity type without creating listener bean instances
 * or opening database connections.</p>
 *
 * <p>{@link ChangeListenerDataSourceValidator} uses the collected metadata to validate datasource
 * configuration. {@link ChangeListenerRegistrationHandler} registers the methods with the selected
 * notification provider during application startup.</p>
 */
@Internal
@Singleton
public final class ChangeListenerRegistry implements ExecutableMethodProcessor<ChangeListener> {
    private final Map<String, List<ChangeListenerMethod>> listeners = new LinkedHashMap<>();

    /**
     * Records a discovered listener method and its entity type under the datasource specified by
     * {@link ChangeListener#dataSource()}, using {@code default} when no datasource is specified.
     *
     * @param beanDefinition the definition that owns the listener method
     * @param method the discovered listener method
     * @param <B> the listener bean type
     * @throws IllegalStateException if the method does not declare exactly one argument or its
     * entity type is missing
     */
    @Override
    public synchronized <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        Argument<?>[] arguments = method.getArguments();
        if (arguments.length != 1) {
            throw invalidChangeListener(method, "must declare exactly one ChangeEvent argument");
        }
        Argument<?> entityArgument = arguments[0].getFirstTypeVariable()
            .orElseThrow(() -> invalidChangeListener(method, "must declare ChangeEvent<E> with a concrete entity type"));
        String datasource = method.stringValue(ChangeListener.class, "dataSource").orElse("default");
        listeners.computeIfAbsent(datasource, ignored -> new ArrayList<>())
            .add(new ChangeListenerMethod(beanDefinition, method, entityArgument));
    }

    /**
     * Returns an immutable snapshot of the listener methods for the given datasource.
     * Later discoveries do not affect the returned list.
     *
     * @param datasource the datasource name
     * @return the listener methods, or an empty list when none use the datasource
     */
    public synchronized List<ChangeListenerMethod> getListeners(String datasource) {
        return List.copyOf(listeners.getOrDefault(datasource, List.of()));
    }

    /**
     * Returns an immutable snapshot of all listener methods grouped by datasource.
     * Both the map and its lists are immutable; later discoveries do not affect them.
     *
     * @return the datasource-to-listeners mapping
     */
    public synchronized Map<String, List<ChangeListenerMethod>> getListeners() {
        Map<String, List<ChangeListenerMethod>> snapshot = new LinkedHashMap<>();
        listeners.forEach((datasource, methods) -> snapshot.put(datasource, List.copyOf(methods)));
        return Collections.unmodifiableMap(snapshot);
    }

    private static IllegalStateException invalidChangeListener(ExecutableMethod<?, ?> method, String message) {
        return new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + message);
    }
}
