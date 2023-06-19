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
package io.micronaut.data.connection.jdbc;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * Unwraps transactional data source proxies.
 *
 * @author Vladimir Kulev
 * @since 1.0.1
 */
@Singleton
@Internal
public class DelegatingDataSourceResolver implements DataSourceResolver {

    @Override
    public DataSource resolve(DataSource dataSource) {
        return DelegatingDataSource.unwrapDataSource(dataSource);
    }

}
