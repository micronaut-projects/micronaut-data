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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;

import javax.sql.DataSource;
import java.util.List;

/**
 * Registers the listeners collected by {@link ChangeListenerRegistry} for one datasource.
 *
 * <p>{@link EachBean} creates a separate registration handler for every {@link DataSource}.
 * Registration is deferred to {@link StartupEvent}, after schema generation. The handler uses
 * a connection from its datasource to select a notification provider, then registers listeners
 * after that connection has been released. Providers remain uninitialized when no listeners
 * select this datasource.</p>
 */
@Context
@EachBean(DataSource.class)
@Requires(beans = ChangeNotificationProvider.class)
@Requires(property = "micronaut.data.jdbc.notifications.enabled", value = "true", defaultValue = "true")
final class ChangeListenerRegistrationHandler implements ApplicationEventListener<StartupEvent> {

    private final String dataSourceName;
    private final JdbcRepositoryOperations operations;
    private final BeanProvider<ChangeNotificationProviderResolver> providerResolver;
    private final ChangeListenerRegistry registry;

    ChangeListenerRegistrationHandler(@Parameter String dataSourceName,
                                      @Parameter JdbcRepositoryOperations operations,
                                      BeanProvider<ChangeNotificationProviderResolver> providerResolver,
                                      ChangeListenerRegistry registry) {
        this.dataSourceName = dataSourceName;
        this.operations = operations;
        this.providerResolver = providerResolver;
        this.registry = registry;
    }

    /**
     * Resolves the notification provider and registers the listener methods collected for this
     * datasource.
     *
     * <p>This runs after schema generation has completed. Deferring provider resolution until this
     * event avoids opening a datasource connection while executable methods are being discovered.</p>
     *
     * @param event The application startup event.
     */
    @Override
    public void onApplicationEvent(StartupEvent event) {
        List<ChangeListenerMethod> listenerMethods = registry.getListeners(dataSourceName);
        if (listenerMethods.isEmpty()) {
            return;
        }
        ChangeNotificationProviderResolver resolver = providerResolver.get();
        ChangeNotificationProvider provider = operations.execute(connection -> {
            ChangeNotificationProvider resolved = resolver.resolve(connection);
            if (resolved == null) {
                throw new IllegalStateException("@ChangeListener datasource [" + dataSourceName + "] has no change notification provider");
            }
            return resolved;
        });
        provider.register(dataSourceName, operations, listenerMethods);
    }
}
