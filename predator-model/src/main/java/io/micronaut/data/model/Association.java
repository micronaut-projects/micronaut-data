package io.micronaut.data.model;


import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.Relation;


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
    @Nullable
    PersistentEntity getAssociatedEntity();

    /**
     * @return The relationship kind
     */
    default @NonNull Relation.Kind getKind() {
        return getAnnotationMetadata().getValue(Relation.class, Relation.Kind.class).orElse(Relation.Kind.ONE_TO_ONE);
    }
}
