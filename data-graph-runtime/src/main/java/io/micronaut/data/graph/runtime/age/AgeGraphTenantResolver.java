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

import org.jspecify.annotations.Nullable;

/**
 * Names the Apache AGE graph of the current tenant, for applications that keep a graph per tenant.
 *
 * <p>A bean of this type is created when the multi-tenancy mode is {@code SCHEMA}, since a graph is a schema of the
 * database: it names the graph after the tenant through {@link AgeConfiguration#tenantGraph(String)}. Declare a bean
 * of this type to resolve the graph another way. Operations fall back to the configured graph of their data source
 * when the resolver returns {@code null}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@FunctionalInterface
public interface AgeGraphTenantResolver {

    /**
     * Resolves the graph of the current tenant. The name must be a plain identifier, as it is written into statements.
     *
     * @return The graph name, or null when there is no tenant
     */
    @Nullable
    String resolveTenantGraph();
}
