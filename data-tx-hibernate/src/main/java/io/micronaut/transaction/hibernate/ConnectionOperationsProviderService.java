package io.micronaut.transaction.hibernate;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;
import org.hibernate.Session;
import org.hibernate.service.Service;

@Internal
record ConnectionOperationsProviderService(
    BeanProvider<ConnectionOperations<Session>> provider) implements Service {
}
