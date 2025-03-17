/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentCollectionAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCollectionJoin;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.PersistentEntityJoin;
import io.micronaut.data.model.jpa.criteria.PersistentEntityListJoin;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySetJoin;
import io.micronaut.data.model.jpa.criteria.PersistentListAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentSetAssociationPath;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The abstract implementation of {@link PersistentEntityFrom}.
 *
 * @param <J> The associated entity owner type
 * @param <E> The association entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPersistentEntityJoinSupport<J, E> implements PersistentEntityFrom<J, E> {

    protected final Map<String, PersistentAssociationPath<E, ?>> joins = new LinkedHashMap<>();

    @Override
    public abstract PersistentEntity getPersistentEntity();

    protected abstract <Y> PersistentAssociationPath<E, Y> createJoinAssociation(@NonNull Association association,
                                                                                 @Nullable io.micronaut.data.annotation.Join.Type type,
                                                                                 @Nullable String alias);

    private <X, Y> PersistentAssociationPath<X, Y> getJoin(String attributeName) {
        return getJoin(attributeName, null, null);

    }

    private <X, Y> PersistentAssociationPath<X, Y> getJoin(String attributeName, io.micronaut.data.annotation.Join.Type type) {
        return getJoin(attributeName, type, null);
    }

    private <X, Y> PersistentAssociationPath<X, Y> getJoin(String attributeName, io.micronaut.data.annotation.Join.Type type, String alias) {
        PersistentProperty persistentProperty = getPersistentEntity().getPropertyByName(attributeName);

        if (persistentProperty == null && attributeName.contains(".")) {
            int periodIndex = attributeName.indexOf(".");
            String owner = attributeName.substring(0, periodIndex);
            PersistentAssociationPath<E, ?> persistentAssociationPath;
            if (joins.containsKey(owner)) {
                persistentAssociationPath = joins.get(owner);
            } else {
                persistentAssociationPath = (PersistentAssociationPath<E, ?>) join(owner, type);
            }
            String remainingJoinPath = attributeName.substring(periodIndex + 1);
            return alias == null ? (PersistentAssociationPath<X, Y>) persistentAssociationPath.join(remainingJoinPath, type)
                : (PersistentAssociationPath<X, Y>) persistentAssociationPath.join(remainingJoinPath, type, alias);
        }

        if (!(persistentProperty instanceof Association association)) {
            throw new IllegalStateException("Expected an association for attribute name: " + attributeName);
        }

        PersistentAssociationPath<E, ?> path = joins.computeIfAbsent(attributeName, a -> createJoinAssociation(association, type, alias));

        if (type != null && type != io.micronaut.data.annotation.Join.Type.DEFAULT) {
            path.setAssociationJoinType(type);
        }
        if (alias != null) {
            path.setAlias(alias);
        }
        return (PersistentAssociationPath<X, Y>) path;
    }

    private <Y> PersistentCollectionAssociationPath<E, Y> getCollectionJoin(String attributeName, io.micronaut.data.annotation.Join.Type type) {
        PersistentAssociationPath<E, Y> join = getJoin(attributeName, type);
        if (!(join instanceof PersistentCollectionAssociationPath<E, Y> persistentCollectionAssociationPath)) {
            throw new IllegalStateException("Join is not a Collection!");
        }
        return persistentCollectionAssociationPath;
    }

    private <Y> PersistentSetAssociationPath<E, Y> getSetJoin(String attributeName, io.micronaut.data.annotation.Join.Type type) {
        PersistentAssociationPath<E, Y> join = getJoin(attributeName, type);
        if (!(join instanceof PersistentSetAssociationPath<E, Y> persistentSetAssociationPath)) {
            throw new IllegalStateException("Join is not a Set!");
        }
        return persistentSetAssociationPath;
    }

    private <Y> PersistentListAssociationPath<E, Y> getListJoin(String attributeName, io.micronaut.data.annotation.Join.Type type) {
        PersistentAssociationPath<E, Y> join = getJoin(attributeName, type);
        if (!(join instanceof PersistentListAssociationPath<E, Y> persistentListAssociationPath)) {
            throw new IllegalStateException("Join is not a List!");
        }
        return persistentListAssociationPath;
    }

    @Override
    public <K, V, M extends Map<K, V>> Expression<M> get(MapAttribute<E, K, V> map) {
        return get(map.getName());
    }

    @Override
    public <K, C extends Collection<K>> Expression<C> get(PluralAttribute<E, C, K> collection) {
        return get(collection.getName());
    }

    @Override
    public <X, Y> PersistentEntityJoin<X, Y> join(String attributeName) {
        return getJoin(attributeName);
    }

    @Override
    public <X, Y> PersistentEntityJoin<X, Y> join(String attributeName, JoinType jt) {
        return getJoin(attributeName, convert(Objects.requireNonNull(jt)));
    }

    @Override
    public <X, Y> PersistentEntityJoin<X, Y> join(String attributeName, io.micronaut.data.annotation.Join.Type type) {
        return getJoin(attributeName, Objects.requireNonNull(type));
    }

    @Override
    public <X, Y> PersistentEntityJoin<X, Y> join(String attributeName, io.micronaut.data.annotation.Join.Type type, String alias) {
        return getJoin(attributeName, Objects.requireNonNull(type), Objects.requireNonNull(alias));
    }

    @Nullable
    private io.micronaut.data.annotation.Join.Type convert(@Nullable JoinType joinType) {
        if (joinType == null) {
            return null;
        }
        return switch (joinType) {
            case LEFT -> io.micronaut.data.annotation.Join.Type.LEFT_FETCH;
            case RIGHT -> io.micronaut.data.annotation.Join.Type.RIGHT_FETCH;
            case INNER -> io.micronaut.data.annotation.Join.Type.INNER;
        };
    }

    @Override
    public <Y> PersistentEntityJoin<E, Y> join(SingularAttribute<? super E, Y> attribute) {
        return getJoin(attribute.getName());
    }

    @Override
    public <Y> PersistentEntityJoin<E, Y> join(SingularAttribute<? super E, Y> attribute, JoinType jt) {
        return getJoin(attribute.getName(), convert(Objects.requireNonNull(jt)));
    }

    @Override
    public <Y> PersistentEntityCollectionJoin<E, Y> join(CollectionAttribute<? super E, Y> collection, JoinType jt) {
        return getCollectionJoin(collection.getName(), convert(jt));
    }

    @Override
    public <Y> PersistentEntityCollectionJoin<E, Y> join(CollectionAttribute<? super E, Y> collection) {
        return getCollectionJoin(collection.getName(), null);
    }

    @Override
    public <Y> PersistentEntitySetJoin<E, Y> join(SetAttribute<? super E, Y> set) {
        return getSetJoin(set.getName(), null);
    }

    @Override
    public <Y> PersistentEntityListJoin<E, Y> join(ListAttribute<? super E, Y> list) {
        return getListJoin(list.getName(), null);
    }

    @Override
    public <K, V> MapJoin<E, K, V> join(MapAttribute<? super E, K, V> map) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> PersistentEntitySetJoin<E, Y> join(SetAttribute<? super E, Y> set, JoinType jt) {
        return getSetJoin(set.getName(), convert(Objects.requireNonNull(jt)));
    }

    @Override
    public <Y> PersistentEntityListJoin<E, Y> join(ListAttribute<? super E, Y> list, JoinType jt) {
        return getListJoin(list.getName(), convert(Objects.requireNonNull(jt)));
    }

    @Override
    public <K, V> MapJoin<E, K, V> join(MapAttribute<? super E, K, V> map, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> PersistentEntityCollectionJoin<X, Y> joinCollection(String attributeName) {
        return (PersistentEntityCollectionJoin<X, Y>) getCollectionJoin(attributeName, null);
    }

    @Override
    public <X, Y> PersistentEntitySetJoin<X, Y> joinSet(String attributeName) {
        return (PersistentEntitySetJoin<X, Y>) getSetJoin(attributeName, null);
    }

    @Override
    public <X, Y> PersistentEntityListJoin<X, Y> joinList(String attributeName) {
        return (PersistentEntityListJoin<X, Y>) getListJoin(attributeName, null);
    }

    @Override
    public <X, K, V> MapJoin<X, K, V> joinMap(String attributeName) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> PersistentEntityCollectionJoin<X, Y> joinCollection(String attributeName, JoinType jt) {
        return (PersistentEntityCollectionJoin<X, Y>) getCollectionJoin(attributeName, null);
    }

    @Override
    public <X, Y> PersistentEntitySetJoin<X, Y> joinSet(String attributeName, JoinType jt) {
        return (PersistentEntitySetJoin<X, Y>) getSetJoin(attributeName, convert(Objects.requireNonNull(jt)));
    }

    @Override
    public <X, Y> PersistentEntityListJoin<X, Y> joinList(String attributeName, JoinType jt) {
        return (PersistentEntityListJoin<X, Y>) getListJoin(attributeName, convert(Objects.requireNonNull(jt)));
    }

    @Override
    public <X, K, V> MapJoin<X, K, V> joinMap(String attributeName, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public Set<Join<E, ?>> getJoins() {
        return new LinkedHashSet<>(joins.values());
    }

    @Override
    public Collection<PersistentAssociationPath<E, ?>> getPersistentJoins() {
        return joins.values();
    }

    @Override
    public boolean isCorrelated() {
        throw notSupportedOperation();
    }

    @Override
    public From<J, E> getCorrelationParent() {
        throw notSupportedOperation();
    }

    @Override
    public Set<Fetch<E, ?>> getFetches() {
        throw notSupportedOperation();
    }

    @Override
    public <Y> Fetch<E, Y> fetch(SingularAttribute<? super E, Y> attribute) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> Fetch<E, Y> fetch(SingularAttribute<? super E, Y> attribute, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> Fetch<E, Y> fetch(PluralAttribute<? super E, ?, Y> attribute) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> Fetch<E, Y> fetch(PluralAttribute<? super E, ?, Y> attribute, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> Fetch<X, Y> fetch(String attributeName) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> Path<Y> get(SingularAttribute<? super E, Y> attribute) {
        return get(attribute.getName());
    }

    @Override
    public Expression<Class<? extends E>> type() {
        throw notSupportedOperation();
    }

}
