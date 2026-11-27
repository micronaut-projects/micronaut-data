package io.micronaut.data.age.r2dbc;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.runtime.multitenancy.TenantResolver;
import jakarta.inject.Singleton;

import java.io.Serializable;

/**
 * Resolves the tenant in the propagated context, as a resolver that reads the request does, else the tenant the test
 * sets, in the contexts that enable multi-tenancy.
 */
@Singleton
@Requires(property = "micronaut.data.multi-tenancy.mode")
public class StaticTenantResolver implements TenantResolver {

    public static volatile String tenant;

    /**
     * The tenant of a request.
     *
     * @param tenant The tenant
     */
    public record Element(String tenant) implements PropagatedContextElement {
    }

    @Override
    public Serializable resolveTenantIdentifier() {
        return PropagatedContext.getOrEmpty().find(Element.class).map(Element::tenant).orElse(tenant);
    }
}
