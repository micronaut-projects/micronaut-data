package example;

import io.micronaut.core.propagation.PropagatedContextElement;

record RuntimeRoutingTenantContext(String tenantId) implements PropagatedContextElement {
}
