package io.micronaut.data.nitrite.runtime.mapping;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.List;

/**
 * Pre-computed metadata for one entity type, cached in {@link NitriteEntityMapper entityMetaCache}.
 *
 * @param <T>               the entity type
 * @param writableProps     the list of writable properties
 * @param mappedByAssocs    the list of mapped-by associations
 * @param idProp            the identity property (if any)
 * @param versionProp       the version property (if any)
 * @param persistentEntity  the cached RuntimePersistentEntity (avoids registry lookup)
 * @param cascadeProps      pre-filtered list of cascade-capable associations (for PERSIST/ALL)
 * @param hasBackReferences true if this entity has any mappedBy associations with back-ref properties
 * @param idAccessor        cached BeanProperty accessor for the ID property
 */
public record NitriteEntityMeta<T>(
    List<WritablePropertyMeta<T>> writableProps,
    List<WritablePropertyMeta<T>> mappedByAssocs,
    @Nullable RuntimePersistentProperty<T> idProp,
    @Nullable RuntimePersistentProperty<T> versionProp,
    RuntimePersistentEntity<T> persistentEntity,
    List<RuntimeAssociation<T>> cascadeProps,
    boolean hasBackReferences,
    @Nullable BeanProperty<T, Object> idAccessor
) {
}
