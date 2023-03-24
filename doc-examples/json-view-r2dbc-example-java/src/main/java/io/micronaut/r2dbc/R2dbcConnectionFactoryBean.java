/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.r2dbc;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * Core factory bean that exposes the following beans.
 *
 * <ul>
 *     <li>The {@link ConnectionFactoryOptions.Builder}</li>
 *     <li>The {@link ConnectionFactoryOptions}</li>
 *     <li>The {@link ConnectionFactory}</li>
 * </ul>
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Factory
public class R2dbcConnectionFactoryBean {

    /**
     * Method that exposes the {@link ConnectionFactoryOptions.Builder}.
     * @param basicR2dbcProperties The basic properties
     * @return The builder
     */
    @EachBean(BasicR2dbcProperties.class)
    protected ConnectionFactoryOptions.Builder connectionFactoryOptionsBuilder(BasicR2dbcProperties basicR2dbcProperties) {
        return basicR2dbcProperties.builder();
    }

    /**
     * Method that exposes the {@link ConnectionFactoryOptions}.
     * @param builder The builder
     * @return The options
     */
    @EachBean(ConnectionFactoryOptions.Builder.class)
    protected ConnectionFactoryOptions connectionFactoryOptions(ConnectionFactoryOptions.Builder builder) {
        return builder.build();
    }

    /**
     * Method that exposes the {@link ConnectionFactory}.
     * @param options the options
     * @return The connection factory
     */
    @EachBean(ConnectionFactoryOptions.class)
    @Context
    protected ConnectionFactory connectionFactory(ConnectionFactoryOptions options) {
        return ConnectionFactories.get(
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "oracle")
                .option(ConnectionFactoryOptions.HOST, "localhost")
                .option(ConnectionFactoryOptions.PORT, 1521)
                .option(ConnectionFactoryOptions.DATABASE, "ORCLCDB")
                .option(ConnectionFactoryOptions.USER, "dbUser")
                .option(ConnectionFactoryOptions.PASSWORD, "DbUserP4ssw0rd")
                .build());
    }
}
