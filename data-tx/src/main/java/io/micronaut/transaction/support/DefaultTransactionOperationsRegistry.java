/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.transaction.support;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionOperationsRegistry;
import io.micronaut.transaction.async.AsyncTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link TransactionOperationsRegistry}.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Internal
@Singleton
final class DefaultTransactionOperationsRegistry implements TransactionOperationsRegistry {

    private final BeanLocator beanLocator;

    DefaultTransactionOperationsRegistry(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public <T extends TransactionOperations<?>> T provideSynchronous(Class<T> transactionOperationsType, String dataSourceName) {
        if (dataSourceName == null) {
            try {
                return beanLocator.getBean(transactionOperationsType, null);
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("No backing TransactionOperations configured. Check your configuration and try again", e);
            }
        }
        try {
            return beanLocator.getBean(transactionOperationsType, Qualifiers.byName(dataSourceName));
        } catch (NoSuchBeanException e) {
            throw new ConfigurationException("No backing TransactionOperations configured for datasource: [" + dataSourceName + "]. Check your configuration and try again", e);
        }
    }

    @Override
    public <T extends ReactiveTransactionOperations<?>> T provideReactive(Class<T> transactionOperationsType, String dataSourceName) {
        if (dataSourceName == null) {
            try {
                return beanLocator.getBean(transactionOperationsType, null);
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("No reactive transaction management has been configured. Ensure you have correctly configured a reactive capable transaction manager");
            }
        }
        try {
            return beanLocator.getBean(transactionOperationsType, Qualifiers.byName(dataSourceName));
        } catch (NoSuchBeanException e) {
            throw new ConfigurationException("No reactive transaction management has been configured for datasource: [" + dataSourceName + "]. Ensure you have correctly configured a reactive capable transaction manager");
        }
    }

    @Override
    public <T extends AsyncTransactionOperations<?>> T provideAsync(Class<T> transactionOperationsType, String dataSourceName) {
        if (dataSourceName == null) {
            try {
                return beanLocator.getBean(transactionOperationsType, null);
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("No reactive transaction management has been configured. Ensure you have correctly configured a async capable transaction manager");
            }
        }
        try {
            return beanLocator.getBean(transactionOperationsType, Qualifiers.byName(dataSourceName));
        } catch (NoSuchBeanException e) {
            throw new ConfigurationException("No reactive transaction management has been configured for datasource: [" + dataSourceName + "]. Ensure you have correctly configured a async capable transaction manager");
        }
    }
}
