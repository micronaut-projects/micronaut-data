package io.micronaut.data.model;


import io.micronaut.data.annotation.Relation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A property that represents an association.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface Association extends PersistentProperty {

    /**
     * The associated entity if any.
     * @return The associated entity
     */
    @Nullable PersistentEntity getAssociatedEntity();

    /**
     * @return The relationship kind
     */
    default @Nonnull Relation.Kind getKind() {
        return getAnnotationMetadata().getValue(Relation.class, Relation.Kind.class).orElse(Relation.Kind.ONE_TO_ONE);
    }
}
