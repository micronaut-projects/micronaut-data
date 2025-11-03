/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.jpa.criteria.PersistentEntityJoin;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.Attribute;

/**
 * The implementation of {@link Fetch}.
 *
 * @param <OwnerType>            The association owner type
 * @param <AssociatedEntityType> The association entity type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class PersistentFetchImpl<OwnerType, AssociatedEntityType> extends PersistentFetchParentImpl<OwnerType, AssociatedEntityType> implements Fetch<OwnerType, AssociatedEntityType> {
    @NonNull
    private final FetchParent<?, OwnerType> parent;
    @NonNull
    private final PersistentEntityJoin<OwnerType, AssociatedEntityType> persistentEntityJoin;

    PersistentFetchImpl(@NonNull FetchParent<?, OwnerType> parent, @NonNull PersistentEntityJoin<OwnerType, AssociatedEntityType> persistentEntityJoin) {
        super(persistentEntityJoin);
        this.parent = parent;
        this.persistentEntityJoin = persistentEntityJoin;
    }

    @Override
    public Attribute<? super OwnerType, ?> getAttribute() {
        return persistentEntityJoin.getAttribute();
    }

    @Override
    public FetchParent<?, OwnerType> getParent() {
        return parent;
    }

    @Override
    public JoinType getJoinType() {
        return persistentEntityJoin.getJoinType();
    }

}
