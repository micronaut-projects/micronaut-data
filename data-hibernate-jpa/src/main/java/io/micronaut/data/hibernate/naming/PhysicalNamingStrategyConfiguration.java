package io.micronaut.data.hibernate.naming;

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;

import javax.inject.Singleton;
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
        jpaConfiguration.getProperties().put(
                AvailableSettings.PHYSICAL_NAMING_STRATEGY, physicalNamingStrategy
        );
        return jpaConfiguration;
    }
}
