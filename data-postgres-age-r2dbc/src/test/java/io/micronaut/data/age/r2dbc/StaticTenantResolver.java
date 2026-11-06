package io.micronaut.data.age.r2dbc;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.runtime.multitenancy.TenantResolver;
import jakarta.inject.Singleton;

import java.io.Serializable;

/**
 * Resolves the tenant the test sets, in the contexts that enable multi-tenancy.
 */
@Singleton
@Requires(property = "micronaut.data.multi-tenancy.mode")
public class StaticTenantResolver implements TenantResolver {

    public static volatile String tenant;

    @Override
    public Serializable resolveTenantIdentifier() {
        return tenant;
    }
}
