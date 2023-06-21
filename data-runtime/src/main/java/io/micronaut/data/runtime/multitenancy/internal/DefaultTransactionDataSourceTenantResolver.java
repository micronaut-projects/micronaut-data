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
package io.micronaut.data.runtime.multitenancy.internal;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.runtime.multitenancy.DataSourceTenantResolver;
import io.micronaut.transaction.interceptor.TransactionDataSourceTenantResolver;
import jakarta.inject.Singleton;

/**
 * The default implementation of {@link TransactionDataSourceTenantResolver} enabling data source multi-tenancy for the transactional manager.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Internal
@Singleton
@Requires(beans = DataSourceTenantResolver.class, classes = TransactionDataSourceTenantResolver.class)
final class DefaultTransactionDataSourceTenantResolver implements TransactionDataSourceTenantResolver {

    private final DataSourceTenantResolver tenantResolver;

    DefaultTransactionDataSourceTenantResolver(DataSourceTenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public String resolveTenantDataSourceName() {
        return tenantResolver.resolveTenantDataSourceName();
    }
}
