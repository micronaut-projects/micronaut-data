package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.Persisted;


/**
 * A strategy interface for resolving the mapped name of an entity or property.
 *
 * @author graemerocher
 * @since 1.0
 */
@FunctionalInterface
public interface NamingStrategy {

    /**
     * Return the mapped name for the given name.
     * @param name The name
     * @return The mapped name
     */
    @NonNull
    String mappedName(@NonNull String name);

    /**
     * Return the mapped name for the given entity.
     * @param entity The entity
     * @return The mapped name
     */
    default @NonNull String mappedName(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        return entity.getAnnotationMetadata().getValue(Persisted.class, String.class)
                .orElseGet(() -> mappedName(entity.getName()));
    }

    /**
     * Return the mapped name for the given property.
     * @param property The property
     * @return The mapped name
     */
    default @NonNull String mappedName(@NonNull PersistentProperty property) {
        ArgumentUtils.requireNonNull("property", property);
        return property.getAnnotationMetadata().getValue(Persisted.class, String.class)
                .orElseGet(() -> mappedName(property.getName()));
    }
}
