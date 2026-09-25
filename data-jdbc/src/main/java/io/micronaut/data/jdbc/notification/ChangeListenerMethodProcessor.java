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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Discovers {@link ChangeListener} methods and delegates their registration to a database
 * notification provider.
 *
 * <p>{@link EachBean} creates a separate processor for every {@link DataSource}. During
 * executable-method processing, a processor collects only listeners that select its datasource
 * through {@link ChangeListener#dataSource()}. A listener without an explicit selection belongs
 * to the {@code default} datasource. Listener discovery and registration are therefore isolated
 * per datasource.</p>
 *
 * <p>Registration is deferred to {@link StartupEvent}, after schema generation. The processor
 * then uses a connection from its datasource to select a notification provider. Registration
 * begins after that connection has been released.</p>
 */
@Context
@EachBean(DataSource.class)
@Requires(beans = ChangeNotificationProvider.class)
final class ChangeListenerMethodProcessor implements ExecutableMethodProcessor<ChangeListener>,
    ApplicationEventListener<StartupEvent> {

    private final String dataSourceName;
    private final JdbcRepositoryOperations operations;
    private final ChangeNotificationProviderResolver providerResolver;
    private final List<ChangeListenerMethod> listenerMethods = new CopyOnWriteArrayList<>();

    ChangeListenerMethodProcessor(@Parameter String dataSourceName,
                                  @Parameter JdbcRepositoryOperations operations,
                                  ChangeNotificationProviderResolver providerResolver) {
        this.dataSourceName = dataSourceName;
        this.operations = operations;
        this.providerResolver = providerResolver;
    }

    /**
     * Binds a discovered listener method to this processor's datasource for deferred registration.
     *
     * <p>Each processor represents one datasource. A method is recorded only when its
     * {@link ChangeListener#dataSource() selected datasource} matches that datasource. The
     * processor retains the listener bean, executable method, and entity type until application
     * startup, when the selected notification provider registers the listener with the database.</p>
     *
     * @param beanDefinition The listener bean definition.
     * @param method The discovered listener method.
     * @param <B> The listener bean type.
     */
    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        if (!dataSourceName.equals(method.stringValue(ChangeListener.class, "dataSource").orElse("default"))) {
            return;
        }
        Argument<?>[] arguments = method.getArguments();
        if (arguments.length != 1) {
            throw invalidChangeListener(method, "must declare exactly one ChangeEvent argument");
        }
        Argument<?> entityArgument = arguments[0].getFirstTypeVariable()
            .orElseThrow(() -> invalidChangeListener(method, "must declare ChangeEvent<E> with a concrete entity type"));
        listenerMethods.add(new ChangeListenerMethod(beanDefinition, method, entityArgument));
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
        if (listenerMethods.isEmpty()) {
            return;
        }
        ChangeNotificationProvider provider = operations.execute(connection -> {
            ChangeNotificationProvider resolved = providerResolver.resolve(connection);
            if (resolved == null) {
                throw new IllegalStateException("@ChangeListener datasource [" + dataSourceName + "] has no change notification provider");
            }
            return resolved;
        });
        provider.register(dataSourceName, operations, listenerMethods);
    }

    private static IllegalStateException invalidChangeListener(ExecutableMethod<?, ?> method, String message) {
        return new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + message);
    }
}
