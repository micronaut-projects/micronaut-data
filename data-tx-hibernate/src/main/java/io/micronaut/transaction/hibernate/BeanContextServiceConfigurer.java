package io.micronaut.transaction.hibernate;

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.configuration.hibernate.jpa.conf.serviceregistry.builder.configures.StandardServiceRegistryBuilderConfigurer;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.hibernate.Session;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

@Requires(classes = StandardServiceRegistryBuilderConfigurer.class)
@Internal
@Prototype
final class BeanContextServiceConfigurer implements StandardServiceRegistryBuilderConfigurer {

    private final BeanContext beanContext;

    BeanContextServiceConfigurer(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void configure(JpaConfiguration jpaConfiguration, StandardServiceRegistryBuilder standardServiceRegistryBuilder) {
        BeanProvider<ConnectionOperations<Session>> connectionOperations = beanContext.getBean(
            Argument.of(
                BeanProvider.class,
                Argument.of(ConnectionOperations.class, Session.class)
            ),
            Qualifiers.byName(jpaConfiguration.getName())
        );
        standardServiceRegistryBuilder.addService(ConnectionOperationsProviderService.class, new ConnectionOperationsProviderService(connectionOperations));
    }

}
