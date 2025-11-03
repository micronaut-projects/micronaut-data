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
