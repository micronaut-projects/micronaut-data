/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.transaction.hibernate;

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.configuration.hibernate.jpa.conf.serviceregistry.builder.configures.StandardServiceRegistryBuilderConfigurer;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.connection.ConnectionOperations;
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
