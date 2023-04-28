package io.micronaut.data.mongodb.session;

import com.mongodb.client.ClientSession;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.manager.ConnectionDefinition;

import java.sql.Connection;

public record ClientSessionPropagatedContextElement(
    String name,
    ClientSession clientSession,
    ConnectionDefinition definition
) implements PropagatedContextElement {
}
