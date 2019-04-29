package io.micronaut.data.model;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Models a persistent entity and provides an API that can be used both within the compiler and at runtime.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface PersistentEntity extends AnnotationMetadataProvider {

    /**
     * The entity name including any package prefix
     *
     * @return The entity name
     */
    @Nonnull String getName();

    /**
     * The composite id
     *
     * @return The composite id or null if there isn't one
     */
    @Nullable PersistentProperty[] getCompositeIdentity();

    /**
     * Returns the identity of the instance
     *
     * @return The identity or null if there isn't one
     */
    @Nullable PersistentProperty getIdentity();

    /**
     * Returns the version property.
     *
     * @return the property
     */
    @Nullable PersistentProperty getVersion();

    /**
     * Is the entity versioned for optimistic locking.
     *
     * @return true if versioned
     */
    default boolean isVersioned() {
        return getVersion() != null;
    }

    /**
     * A list of properties to be persisted
     * @return A list of PersistentProperty instances
     */
    @Nonnull List<PersistentProperty> getPersistentProperties();

    /**
     * A list of the associations for this entity. This is typically
     * a subset of the list returned by {@link #getPersistentProperties()}
     *
     * @return A list of associations
     */
    @Nonnull List<Association> getAssociations();

    /**
     * A list of embedded associations for this entity. This is typically
     * a subset of the list returned by {@link #getPersistentProperties()}
     *
     * @return A list of associations
     */
    @Nonnull List<Embedded> getEmbedded();

    /**
     * Obtains a PersistentProperty instance by name
     *
     * @param name The name of the property
     * @return The PersistentProperty or null if it doesn't exist
     */
    @Nullable PersistentProperty getPropertyByName(String name);

    /**
     * A list of property names that a persistent
     * @return A List of strings
     */
    @Nonnull List<String> getPersistentPropertyNames();

    /**
     * @return Returns the name of the class decapitalized form
     */
    default @Nonnull String getDecapitalizedName() {
        return NameUtils.decapitalize(NameUtils.getSimpleName(getName()));
    }

    /**
     * Returns whether the specified entity asserts ownership over this
     * entity
     *
     * @param owner The owning entity
     * @return True if it does own this entity
     */
    boolean isOwningEntity(PersistentEntity owner);

    /**
     * Returns the parent entity of this entity
     * @return The ParentEntity instance
     */
    @Nullable PersistentEntity getParentEntity();

    /**
     * Obtains the root entity of an inheritance hierarchy
     * @return The root entity
     */
    default @Nonnull PersistentEntity getRootEntity() {
        return this;
    }

    /**
     * Whether this entity is a root entity
     * @return True if it is a root entity
     */
    default boolean isRoot() {
        return getRootEntity() == this;
    }

    /**
     * Creates a new persistent entity representation of the given type. The type
     * must be annotated with {@link io.micronaut.core.annotation.Introspected}.
     *
     * @param type The type
     * @return The entity
     */
    static @Nonnull PersistentEntity of(@Nonnull Class<?> type) {
        ArgumentUtils.requireNonNull("type", type);
        return new RuntimePersistentEntity(type);
    }

    /**
     * Creates a new persistent entity representation of the given type. The type
     * must be annotated with {@link io.micronaut.core.annotation.Introspected}.
     *
     * @param introspection The introspection
     * @return The entity
     */
    static @Nonnull PersistentEntity of(@Nonnull BeanIntrospection<?> introspection) {
        ArgumentUtils.requireNonNull("introspection", introspection);
        return new RuntimePersistentEntity(introspection);
    }

}
