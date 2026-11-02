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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Validates that every listener in {@link ChangeListenerRegistry} selects a configured datasource.
 * Runs before datasource-specific handlers begin registration, including when no datasource or
 * notification provider is available. Providers remain uninitialized when the registry is empty.
 */
@Context
@Order(Ordered.HIGHEST_PRECEDENCE)
@Requires(property = "micronaut.data.jdbc.notifications.enabled", value = "true", defaultValue = "true")
final class ChangeListenerDataSourceValidator implements ApplicationEventListener<StartupEvent> {

    private final BeanContext beanContext;
    private final BeanProvider<ChangeNotificationProviderResolver> providerResolver;
    private final ChangeListenerRegistry registry;

    ChangeListenerDataSourceValidator(BeanContext beanContext, BeanProvider<ChangeNotificationProviderResolver> providerResolver,
                                      ChangeListenerRegistry registry) {
        this.beanContext = beanContext;
        this.providerResolver = providerResolver;
        this.registry = registry;
    }

    /**
     * Fails startup if any listener references a datasource without a matching bean.
     *
     * @param event the application startup event
     */
    @Override
    public void onApplicationEvent(StartupEvent event) {
        Map<String, List<ChangeListenerMethod>> listeners = registry.getListeners();
        if (listeners.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<ChangeListenerMethod>> entry : listeners.entrySet()) {
            if (!beanContext.containsBean(DataSource.class, Qualifiers.byName(entry.getKey()))) {
                ChangeListenerMethod listener = entry.getValue().get(0);
                throw new IllegalStateException("@ChangeListener method [" + listener.method().getDescription(true)
                    + "] selects datasource [" + entry.getKey() + "] but no matching DataSource bean is configured");
            }
        }
        if (!providerResolver.get().hasProviders()) {
            ChangeListenerMethod listener = listeners.values().iterator().next().get(0);
            throw new IllegalStateException("@ChangeListener method [" + listener.method().getDescription(true)
                + "] cannot be registered because no change notification provider is available");
        }
    }

}
