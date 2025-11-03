package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.data.annotation.Join.Type.FETCH;
import static io.micronaut.data.annotation.Join.Type.INNER_FETCH;
import static io.micronaut.data.annotation.Join.Type.LEFT_FETCH;
import static io.micronaut.data.annotation.Join.Type.RIGHT_FETCH;

/**
 * The implementation of {@link FetchParent}.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
sealed class PersistentFetchParentImpl<OwnerType, AssociatedEntityType> implements FetchParent<OwnerType, AssociatedEntityType> permits PersistentFetchImpl {
    @NonNull
    private final PersistentEntityFrom<OwnerType, AssociatedEntityType> persistentAssociationPath;

    PersistentFetchParentImpl(@NonNull PersistentEntityFrom<OwnerType, AssociatedEntityType> persistentAssociationPath) {
        this.persistentAssociationPath = persistentAssociationPath;
    }

    @Override
    public Set<Fetch<AssociatedEntityType, ?>> getFetches() {
        return persistentAssociationPath.getPersistentJoins()
            .stream()
            .filter(p -> p.getAssociationJoinType() != null && p.getAssociationJoinType().isFetch())
            .map(p -> new PersistentFetchImpl<>(this, p))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public <Y> Fetch<AssociatedEntityType, Y> fetch(SingularAttribute<? super AssociatedEntityType, Y> attribute) {
        return new PersistentFetchImpl<>(this, persistentAssociationPath.join(attribute, FETCH));
    }

    @Override
    public <Y> Fetch<AssociatedEntityType, Y> fetch(SingularAttribute<? super AssociatedEntityType, Y> attribute, JoinType jt) {
        return new PersistentFetchImpl<>(this, persistentAssociationPath.join(attribute, convert(jt)));
    }

    @Override
    public <Y> Fetch<AssociatedEntityType, Y> fetch(PluralAttribute<? super AssociatedEntityType, ?, Y> attribute) {
        return new PersistentFetchImpl<>(this, persistentAssociationPath.join(attribute.getName(), FETCH));
    }

    @Override
    public <Y> Fetch<AssociatedEntityType, Y> fetch(PluralAttribute<? super AssociatedEntityType, ?, Y> attribute, JoinType jt) {
        return new PersistentFetchImpl<>(this, persistentAssociationPath.join(attribute.getName(), convert(jt)));
    }

    @Override
    public <X, Y> Fetch<X, Y> fetch(String attributeName) {
        PersistentFetchParentImpl<Y, X> thisTyped = (PersistentFetchParentImpl<Y, X>) this;
        return new PersistentFetchImpl<>(thisTyped, thisTyped.persistentAssociationPath.join(attributeName));
    }

    @Override
    public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType jt) {
        PersistentFetchParentImpl<Y, X> thisTyped = (PersistentFetchParentImpl<Y, X>) this;
        return new PersistentFetchImpl<>(thisTyped, thisTyped.persistentAssociationPath.join(attributeName, convert(jt)));
    }

    @Nullable
    private Join.Type convert(@Nullable JoinType joinType) {
        if (joinType == null) {
            return null;
        }
        return switch (joinType) {
            case LEFT -> LEFT_FETCH;
            case RIGHT -> RIGHT_FETCH;
            case INNER -> INNER_FETCH;
        };
    }

}
