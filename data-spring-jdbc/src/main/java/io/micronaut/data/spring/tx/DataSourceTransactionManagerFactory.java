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
package io.micronaut.data.spring.tx;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * Configures a {@link DataSourceTransactionManager} for each configured JDBC {@link DataSource}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(classes = DataSourceTransactionManager.class)
@Internal
public final class DataSourceTransactionManagerFactory {

    /**
     * For each {@link DataSource} add a {@link DataSourceTransactionManager} bean.
     *
     * @param dataSource The data source
     * @return The bean to add
     */
    @EachBean(DataSource.class)
    DataSourceTransactionManager dataSourceTransactionManager(DataSource dataSource) {
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(
            DelegatingDataSource.unwrapDataSource(dataSource)
        );
        dataSourceTransactionManager.afterPropertiesSet();
        return dataSourceTransactionManager;
    }

}
