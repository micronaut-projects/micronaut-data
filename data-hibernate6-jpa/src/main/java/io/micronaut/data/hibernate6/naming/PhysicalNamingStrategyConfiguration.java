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
package io.micronaut.data.hibernate6.naming;

import io.micronaut.configuration.hibernate6.jpa.JpaConfiguration;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;

import jakarta.inject.Singleton;
import java.util.Objects;

/**
 * Applies the configured {@link PhysicalNamingStrategy}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
@Singleton
class PhysicalNamingStrategyConfiguration implements BeanCreatedEventListener<JpaConfiguration> {
    private final PhysicalNamingStrategy physicalNamingStrategy;

    /**
     * Default constructor.
     * @param physicalNamingStrategy The naming strategy
     */
    public PhysicalNamingStrategyConfiguration(PhysicalNamingStrategy physicalNamingStrategy) {
        Objects.requireNonNull(physicalNamingStrategy, "PhysicalNamingStrategy cannot be null");
        this.physicalNamingStrategy = physicalNamingStrategy;
    }

    @Override
    public JpaConfiguration onCreated(BeanCreatedEvent<JpaConfiguration> event) {
        JpaConfiguration jpaConfiguration = event.getBean();
        jpaConfiguration.getProperties().putIfAbsent(
                AvailableSettings.PHYSICAL_NAMING_STRATEGY, physicalNamingStrategy
        );
        return jpaConfiguration;
    }
}
