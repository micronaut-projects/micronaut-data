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
package io.micronaut.data.jdbc.runtime.multitenancy;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;

/**
 * Resolves the JDBC {@link DataSource} to use for a tenant identifier.
 *
 * <p>This can be used together with {@link TenantRoutingDataSource} to lazily resolve
 * or provision tenant-specific data sources at runtime while keeping a stable
 * Micronaut-managed datasource bean.</p>
 *
 * <p>Implementations are expected to cache resolved datasources if resolution is expensive
 * or if datasource identity must remain stable across repeated lookups for the same tenant.</p>
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Experimental
@FunctionalInterface
public interface TenantDataSourceResolver {

    /**
     * Resolve the datasource for the tenant identifier.
     *
     * @param tenantId The tenant identifier
     * @return The datasource for the tenant
     */
    @NonNull
    DataSource resolveTenantDataSource(@NonNull String tenantId);
}
