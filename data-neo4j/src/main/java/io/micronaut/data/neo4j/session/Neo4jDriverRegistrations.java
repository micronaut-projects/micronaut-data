/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.neo4j.session;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

/**
 * Registers the drivers graph repositories can use: the driver of {@code micronaut-neo4j} as {@code default}, and one
 * driver per {@link Neo4jDriverConfiguration}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Factory
@Internal
final class Neo4jDriverRegistrations {

    @Singleton
    @Named("default")
    @Primary
    @Requires(beans = Driver.class)
    Neo4jDriverRegistration defaultDriver(Driver driver) {
        return new Neo4jDriverRegistration("default", driver);
    }

    @EachBean(Neo4jDriverConfiguration.class)
    @Bean(preDestroy = "close")
    Neo4jDriverRegistration configuredDriver(Neo4jDriverConfiguration configuration) {
        Driver driver = configuration.getUsername() == null
            ? GraphDatabase.driver(configuration.getUri(), AuthTokens.none())
            : GraphDatabase.driver(configuration.getUri(), AuthTokens.basic(configuration.getUsername(), configuration.getPassword()));
        return new Neo4jDriverRegistration(configuration.getName(), driver);
    }
}
