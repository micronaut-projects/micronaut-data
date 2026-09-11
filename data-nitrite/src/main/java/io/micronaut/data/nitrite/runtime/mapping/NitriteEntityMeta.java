/*
 * Copyright 2017-2026 original authors
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
