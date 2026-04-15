package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.runtime.multitenancy.TenantResolver;
import jakarta.inject.Singleton;

import java.io.Serializable;

@Singleton
@Requires(property = "example.runtime-routing.enabled", value = "true")
public class RuntimeRoutingTenantResolver implements TenantResolver {

    // tag::tenant-resolver[]
    @Override
    public Serializable resolveTenantIdentifier() {
        return PropagatedContext.getOrEmpty()
            .find(RuntimeRoutingTenantContext.class)
            .map(RuntimeRoutingTenantContext::tenantId)
            .orElse(null);
    }
    // end::tenant-resolver[]
}
