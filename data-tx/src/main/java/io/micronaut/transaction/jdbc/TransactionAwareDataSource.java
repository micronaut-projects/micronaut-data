/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.transaction.jdbc;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Transaction aware data source implementation.
 *
 * @author graemerocher
 * @since 1.0.1
 */
@Singleton
@Requires(missingBeans = io.micronaut.jdbc.spring.DataSourceTransactionManagerFactory.class)
public class TransactionAwareDataSource implements BeanCreatedEventListener<DataSource> {
    private final BeanLocator beanLocator;

    /**
     * Create a new DelegatingDataSource.
     *
     * @param beanLocator The bean locator
     */
    public TransactionAwareDataSource(
            BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        final BeanIdentifier beanIdentifier = event.getBeanIdentifier();
        String name = beanIdentifier.getName();
        if (name.equalsIgnoreCase("primary")) {
            name = "default";
        }
        return new DataSourceProxy(event.getBean(), name);
    }

    /**
     * The transaction aware proxy implementation.
     *
     * @author graemerocher
     * @since 1.0.1
     */
    private final class DataSourceProxy extends DelegatingDataSource {
        private final String qualifier;
        private Connection transactionAwareConnection;

        /**
         * Create a new DelegatingDataSource.
         *
         * @param targetDataSource the target DataSource
         * @param qualifier        The qualifier
         */
        DataSourceProxy(@NonNull DataSource targetDataSource, String qualifier) {
            super(targetDataSource);
            this.qualifier = qualifier;
        }

        @Override
        public Connection getConnection() {
            return getTransactionAwareConnection();
        }

        private Connection getTransactionAwareConnection() {
            if (transactionAwareConnection == null) {
                transactionAwareConnection
                        = beanLocator.getBean(Connection.class, Qualifiers.byName(qualifier));

            }
            return transactionAwareConnection;
        }
    }
}
