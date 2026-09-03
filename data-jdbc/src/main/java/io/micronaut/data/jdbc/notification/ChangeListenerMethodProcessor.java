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
 * then uses operations and a connection from its datasource to select a notification provider and
 * register the listener methods it collected.</p>
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
                                  JdbcRepositoryOperations operations,
                                  ChangeNotificationProviderResolver providerResolver) {
        this.dataSourceName = dataSourceName;
        this.operations = operations;
        this.providerResolver = providerResolver;
    }

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

    @Override
    public void onApplicationEvent(StartupEvent event) {
        // Schema generation completes before StartupEvent, so provider selection and registration
        // do not require a database connection while executable methods are being discovered.
        if (listenerMethods.isEmpty()) {
            return;
        }
        operations.execute(connection -> {
            ChangeNotificationProvider provider = providerResolver.resolve(connection);
            if (provider == null) {
                throw new IllegalStateException("@ChangeListener datasource [" + dataSourceName + "] has no change notification provider");
            }
            provider.register(dataSourceName, operations, listenerMethods);
            return Boolean.TRUE;
        });
    }

    private static IllegalStateException invalidChangeListener(ExecutableMethod<?, ?> method, String message) {
        return new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + message);
    }
}
