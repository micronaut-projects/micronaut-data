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
import io.micronaut.data.runtime.multitenancy.TenantResolver;
import jakarta.inject.Singleton;

import java.io.Serializable;

/**
 * The default implementation of {@link TenantResolver} that delegates to Micronaut Multitenancy project if available.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Internal
@Singleton
@Requires(
    beans = io.micronaut.multitenancy.tenantresolver.TenantResolver.class,
    missingBeans = TenantResolver.class,
    classes = io.micronaut.multitenancy.tenantresolver.TenantResolver.class
)
final class DefaultTenantResolver implements TenantResolver {

    private final io.micronaut.multitenancy.tenantresolver.TenantResolver tenantResolver;

    DefaultTenantResolver(io.micronaut.multitenancy.tenantresolver.TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public Serializable resolveTenantIdentifier() {
        Serializable tenantId = tenantResolver.resolveTenantIdentifier();
        if (tenantId == io.micronaut.multitenancy.tenantresolver.TenantResolver.DEFAULT) {
            return null;
        }
        return tenantId;
    }
}
