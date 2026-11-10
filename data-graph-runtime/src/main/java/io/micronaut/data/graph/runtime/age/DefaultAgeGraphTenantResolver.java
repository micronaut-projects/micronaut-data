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
package io.micronaut.data.graph.runtime.age;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Names the graph of the current tenant after the tenant, through {@link AgeConfiguration#tenantGraph(String)}, when
 * multi-tenancy keeps a schema per tenant. The {@link SchemaTenantResolver} it builds on exists in the {@code SCHEMA}
 * multi-tenancy mode when there is a tenant resolver.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(beans = SchemaTenantResolver.class, missingBeans = AgeGraphTenantResolver.class)
final class DefaultAgeGraphTenantResolver implements AgeGraphTenantResolver {

    private final SchemaTenantResolver tenantResolver;
    private final AgeConfiguration configuration;

    DefaultAgeGraphTenantResolver(SchemaTenantResolver tenantResolver, AgeConfiguration configuration) {
        this.tenantResolver = tenantResolver;
        this.configuration = configuration;
    }

    @Override
    public @Nullable String resolveTenantGraph() {
        String tenant = tenantResolver.resolveTenantSchemaName();
        return tenant == null ? null : configuration.tenantGraph(tenant);
    }
}
