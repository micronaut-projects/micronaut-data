package io.micronaut.data.model;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.GeneratedValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Models a persistent property. That is a property that is saved and retrieved from the database.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PersistentProperty extends AnnotationMetadataProvider {

    /**
     * The name of the property
     * @return The property name
     */
    @Nonnull String getName();

    /**
     * The name with the first letter in upper case as per Java bean conventions
     * @return The capitilized name
     */
    default @Nonnull String getCapitilizedName() {
        return NameUtils.capitalize(getName());
    }

    /**
     * The type of the property
     * @return The property type
     */
    @Nonnull String getTypeName();


    /**
     * Obtains the owner of this persistent property
     *
     * @return The owner
     */
    @Nonnull PersistentEntity getOwner();

    /**
     * Whether the property can be set to null
     *
     * @return True if it can
     */
    default boolean isNullable() {
        return getAnnotationMetadata().hasAnnotation(Nullable.class);
    }

    /**
     * Whether the property is read-only, for example for generated values.
     * @return True if it is read-only
     */
    default boolean isReadOnly() {
        return isGenerated();
    }

    /**
     * @return Whether this property is inherited
     */
    default boolean isInherited() {
        return false;
    }

    /**
     * Whether the property is generated.
     *
     * @return True if is generated
     */
    default boolean isGenerated() {
        return getAnnotationMetadata().hasAnnotation(GeneratedValue.class);
    }
}
