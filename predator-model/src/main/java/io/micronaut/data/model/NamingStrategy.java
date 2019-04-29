package io.micronaut.data.model;

import io.micronaut.data.annotation.Persisted;

import javax.annotation.Nonnull;

/**
 * A strategy interface for resolving the mapped name of an entity or property.
 *
 * @author graemerocher
 * @since 1.0
 */
@FunctionalInterface
public interface NamingStrategy {

    @Nonnull String mappedName(@Nonnull String name);

    default @Nonnull String mappedName(@Nonnull PersistentEntity entity) {
        return entity.getAnnotationMetadata().getValue(Persisted.class, String.class)
                .orElseGet(() -> mappedName(entity.getName()));
    }

    default @Nonnull String mappedName(@Nonnull PersistentProperty property) {
        return property.getAnnotationMetadata().getValue(Persisted.class, String.class)
                .orElseGet(() -> mappedName(property.getName()));
    }
}
