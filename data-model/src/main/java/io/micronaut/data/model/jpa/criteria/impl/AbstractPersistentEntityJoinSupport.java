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
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import jakarta.persistence.criteria.CollectionJoin;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.HashSet;
import java.util.LinkedHashMap;
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
public abstract class AbstractPersistentEntityJoinSupport<J, E> implements PersistentEntityFrom<J, E>, SelectionVisitable {

    protected final Map<String, PersistentAssociationPath> joins = new LinkedHashMap<>();

    public abstract PersistentEntity getPersistentEntity();

    protected abstract <X, Y> PersistentAssociationPath<X, Y> createJoinAssociation(@NonNull Association association,
                                                                                    @Nullable io.micronaut.data.annotation.Join.Type type,
                                                                                    @Nullable String alias);

    @Override
    public Path<?> getParentPath() {
        return null;
    }

    @Override
    public <X, Y> PersistentAssociationPath<X, Y> join(String attributeName) {
        return addJoin(attributeName, null, null);
    }

    @Override
    public <X, Y> PersistentAssociationPath<X, Y> join(String attributeName, io.micronaut.data.annotation.Join.Type type) {
        return addJoin(attributeName, Objects.requireNonNull(type), null);
    }

    @Override
    public <X, Y> PersistentAssociationPath<X, Y> join(String attributeName, io.micronaut.data.annotation.Join.Type type, String alias) {
        return addJoin(attributeName, Objects.requireNonNull(type), Objects.requireNonNull(alias));
    }

    private <X, Y> PersistentAssociationPath<X, Y> addJoin(String attributeName, io.micronaut.data.annotation.Join.Type type, String alias) {
        PersistentProperty persistentProperty = getPersistentEntity().getPropertyByName(attributeName);
        if (!(persistentProperty instanceof Association)) {
            throw new IllegalStateException("Expected an association for attribute name: " + attributeName);
        }

        PersistentAssociationPath path = joins.computeIfAbsent(attributeName, a -> createJoinAssociation((Association) persistentProperty, type, alias));

        if (type != null && type != io.micronaut.data.annotation.Join.Type.DEFAULT) {
            path.setAssociationJoinType(type);
        }
        if (alias != null) {
            path.setAlias(alias);
        }
        return (PersistentAssociationPath<X, Y>) path;
    }

    @Override
    public Set<Join<E, ?>> getJoins() {
        return new HashSet(joins.values());
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
    public <Y> Join<E, Y> join(SingularAttribute<? super E, Y> attribute) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> Join<E, Y> join(SingularAttribute<? super E, Y> attribute, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> CollectionJoin<E, Y> join(CollectionAttribute<? super E, Y> collection) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> SetJoin<E, Y> join(SetAttribute<? super E, Y> set) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> ListJoin<E, Y> join(ListAttribute<? super E, Y> list) {
        throw notSupportedOperation();
    }

    @Override
    public <K, V> MapJoin<E, K, V> join(MapAttribute<? super E, K, V> map) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> CollectionJoin<E, Y> join(CollectionAttribute<? super E, Y> collection, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> SetJoin<E, Y> join(SetAttribute<? super E, Y> set, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> ListJoin<E, Y> join(ListAttribute<? super E, Y> list, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <K, V> MapJoin<E, K, V> join(MapAttribute<? super E, K, V> map, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> CollectionJoin<X, Y> joinCollection(String attributeName) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> SetJoin<X, Y> joinSet(String attributeName) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> ListJoin<X, Y> joinList(String attributeName) {
        throw notSupportedOperation();
    }

    @Override
    public <X, K, V> MapJoin<X, K, V> joinMap(String attributeName) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> Join<X, Y> join(String attributeName, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> CollectionJoin<X, Y> joinCollection(String attributeName, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> SetJoin<X, Y> joinSet(String attributeName, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <X, Y> ListJoin<X, Y> joinList(String attributeName, JoinType jt) {
        throw notSupportedOperation();
    }

    @Override
    public <X, K, V> MapJoin<X, K, V> joinMap(String attributeName, JoinType jt) {
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
        throw notSupportedOperation();
    }

    @Override
    public <K, V, M extends Map<K, V>> Expression<M> get(MapAttribute<E, K, V> map) {
        throw notSupportedOperation();
    }

    @Override
    public <K, C extends java.util.Collection<K>> Expression<C> get(PluralAttribute<E, C, K> collection) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Class<? extends E>> type() {
        throw notSupportedOperation();
    }

    @Override
    public Class<? extends E> getJavaType() {
        throw notSupportedOperation();
    }

}
