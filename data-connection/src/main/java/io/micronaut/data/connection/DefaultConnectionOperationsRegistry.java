/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.connection;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;
import io.micronaut.data.connection.manager.async.AsyncConnectionOperations;
import io.micronaut.data.connection.manager.reactive.ReactiveConnectionOperations;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link ConnectionOperationsRegistry}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@Singleton
final class DefaultConnectionOperationsRegistry implements ConnectionOperationsRegistry {

    private final BeanLocator beanLocator;

    DefaultConnectionOperationsRegistry(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @NonNull
    @Override
    public <T extends ConnectionOperations<?>> T provideSynchronous(Class<T> connectionOperationsType, String dataSourceName) {
        if (dataSourceName == null) {
            try {
                return beanLocator.getBean(connectionOperationsType, null);
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("No backing ConnectionOperations configured. Check your configuration and try again", e);
            }
        }
        try {
            return beanLocator.getBean(connectionOperationsType, Qualifiers.byName(dataSourceName));
        } catch (NoSuchBeanException e) {
            throw new ConfigurationException("No backing ConnectionOperations configured for datasource: [" + dataSourceName + "]. Check your configuration and try again", e);
        }
    }

    @NonNull
    @Override
    public <T extends ReactiveConnectionOperations<?>> T provideReactive(Class<T> connectionOperationsType, String dataSourceName) {
        if (dataSourceName == null) {
            try {
                return beanLocator.getBean(connectionOperationsType, null);
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("No reactive connection management has been configured. Ensure you have correctly configured a reactive capable connection manager");
            }
        }
        try {
            return beanLocator.getBean(connectionOperationsType, Qualifiers.byName(dataSourceName));
        } catch (NoSuchBeanException e) {
            throw new ConfigurationException("No reactive connection management has been configured for datasource: [" + dataSourceName + "]. Ensure you have correctly configured a reactive capable connection manager");
        }
    }

    @NonNull
    @Override
    public <T extends AsyncConnectionOperations<?>> T provideAsync(Class<T> connectionOperationsType, String dataSourceName) {
        if (dataSourceName == null) {
            try {
                return beanLocator.getBean(connectionOperationsType, null);
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("No reactive connection management has been configured. Ensure you have correctly configured a async capable connection manager");
            }
        }
        try {
            return beanLocator.getBean(connectionOperationsType, Qualifiers.byName(dataSourceName));
        } catch (NoSuchBeanException e) {
            throw new ConfigurationException("No reactive connection management has been configured for datasource: [" + dataSourceName + "]. Ensure you have correctly configured a async capable connection manager");
        }
    }
}
