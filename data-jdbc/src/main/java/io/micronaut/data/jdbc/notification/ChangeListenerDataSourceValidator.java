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

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Validates that every discovered {@link ChangeListener} selects a configured datasource.
 *
 * <p>This processor is independent of datasource-specific listener processors and notification
 * providers, so it can detect methods that none of those processors would otherwise claim. It
 * validates at startup before datasource-specific processors begin registration. Notification
 * providers are resolved only when at least one listener has been discovered.</p>
 */
@Context
@Order(Ordered.HIGHEST_PRECEDENCE)
final class ChangeListenerDataSourceValidator implements ExecutableMethodProcessor<ChangeListener>,
    ApplicationEventListener<StartupEvent> {

    private final BeanContext beanContext;
    private final BeanProvider<ChangeNotificationProviderResolver> providerResolver;
    private final List<ListenerDataSource> listeners = new CopyOnWriteArrayList<>();

    ChangeListenerDataSourceValidator(BeanContext beanContext, BeanProvider<ChangeNotificationProviderResolver> providerResolver) {
        this.beanContext = beanContext;
        this.providerResolver = providerResolver;
    }

    /**
     * Records the datasource selected by a discovered listener method.
     *
     * @param beanDefinition the definition that owns the listener method
     * @param method the discovered listener method
     * @param <B> the listener bean type
     */
    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        String dataSourceName = method.stringValue(ChangeListener.class, "dataSource").orElse("default");
        listeners.add(new ListenerDataSource(dataSourceName, method));
    }

    /**
     * Fails startup if any listener references a datasource without a matching bean.
     *
     * @param event the application startup event
     */
    @Override
    public void onApplicationEvent(StartupEvent event) {
        if (listeners.isEmpty()) {
            return;
        }
        for (ListenerDataSource listener : listeners) {
            if (!beanContext.containsBean(DataSource.class, Qualifiers.byName(listener.dataSourceName()))) {
                throw new IllegalStateException("@ChangeListener method [" + listener.method().getDescription(true)
                    + "] selects datasource [" + listener.dataSourceName() + "] but no matching DataSource bean is configured");
            }
        }
        if (!providerResolver.get().hasProviders()) {
            ListenerDataSource listener = listeners.get(0);
            throw new IllegalStateException("@ChangeListener method [" + listener.method().getDescription(true)
                + "] cannot be registered because no change notification provider is available");
        }
    }

    private record ListenerDataSource(String dataSourceName, ExecutableMethod<?, ?> method) {
    }
}
