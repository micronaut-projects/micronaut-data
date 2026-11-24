package io.micronaut.data.neo4j.features;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import jakarta.inject.Singleton;

/**
 * A naming strategy that serde can only look up at runtime.
 */
@Singleton
public class PrefixNamingStrategy implements PropertyNamingStrategy {
    @Override
    public String translate(AnnotatedElement element) {
        return "x_" + element.getName();
    }
}
