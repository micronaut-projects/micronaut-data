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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.Collection;
import java.util.Map;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The persistent entity association path.
 *
 * @param <OwnerType>            The association owner type
 * @param <AssociatedEntityType> The association entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentAssociationPath<OwnerType, AssociatedEntityType> extends PersistentEntityJoin<OwnerType, AssociatedEntityType>,
        PersistentAssociationAttributePath<OwnerType, AssociatedEntityType> {

    @Override

    default Join<OwnerType, AssociatedEntityType> on(Expression<Boolean> restriction) {
        throw notSupportedOperation();
    }

    @Override

    default Join<OwnerType, AssociatedEntityType> on(Predicate... restrictions) {
        throw notSupportedOperation();
    }

    @Override
    @Nullable
    default Predicate getOn() {
        throw notSupportedOperation();
    }

    @Override

    default Attribute<? super OwnerType, ?> getAttribute() {
        throw notSupportedOperation();
    }

    @Override

    default Bindable<AssociatedEntityType> getModel() {
        throw notSupportedOperation();
    }

    @Override
    <X, Y> PersistentAssociationPath<X, Y> join(String attributeName);

    @Override
    <X, Y> PersistentAssociationPath<X, Y> join(String attributeName, io.micronaut.data.annotation.Join. @Nullable Type joinType);

    @Override
    <X, Y> PersistentAssociationPath<X, Y> join(String attributeName, io.micronaut.data.annotation.Join. @Nullable Type joinType, @Nullable String alias);

    @Override
    <X, Y> PersistentAssociationPath<X, Y> join(String attributeName, JoinType jt);

    @Override
    <Y> PersistentAssociationPath<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute, JoinType jt);

    @Override
    <Y> PersistentAssociationPath<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute);

    @Override
    <Y> PersistentAssociationPath<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute, io.micronaut.data.annotation.Join.Type jt);

    @Override
    <X, Y> PersistentPluralAssociationPath<X, Y> joinPlural(String attributeName);

    @Override
    <X, Y> PersistentPluralAssociationPath<X, Y> joinPlural(String attributeName, JoinType jt);

    @Override
    <X, Y> PersistentPluralAssociationPath<X, Y> joinPlural(String attributeName, io.micronaut.data.annotation.Join.Type jt);

    @Override
    <Y, C extends Collection<Y>> PersistentPluralAssociationPath<AssociatedEntityType, Y> joinPlural(PluralAttribute<? super AssociatedEntityType, C, Y> attribute);

    @Override
    <Y, C extends Collection<Y>> PersistentPluralAssociationPath<AssociatedEntityType, Y> joinPlural(PluralAttribute<? super AssociatedEntityType, C, Y> attribute, JoinType jt);

    @Override
    <Y, C extends Collection<Y>> PersistentPluralAssociationPath<AssociatedEntityType, Y> joinPlural(PluralAttribute<? super AssociatedEntityType, C, Y> attribute, io.micronaut.data.annotation.Join.Type jt);

    @Override
    <K, V, M extends Map<K, V>> PersistentMapAttributePath<AssociatedEntityType, V> joinMapPath(String attributeName);

    @Override
    <K, V, M extends Map<K, V>> PersistentMapAttributePath<AssociatedEntityType, V> joinMapPath(String attributeName, JoinType jt);

    @Override
    <K, V, M extends Map<K, V>> PersistentMapAttributePath<AssociatedEntityType, V> joinMapPath(String attributeName, io.micronaut.data.annotation.Join.Type jt);

    @Override
    <K, V, M extends Map<K, V>> PersistentMapAttributePath<AssociatedEntityType, V> joinMapPath(MapAttribute<? super AssociatedEntityType, K, V> attribute);

    @Override
    <K, V, M extends Map<K, V>> PersistentMapAttributePath<AssociatedEntityType, V> joinMapPath(MapAttribute<? super AssociatedEntityType, K, V> attribute, JoinType jt);

    @Override
    <K, V, M extends Map<K, V>> PersistentMapAttributePath<AssociatedEntityType, V> joinMapPath(MapAttribute<? super AssociatedEntityType, K, V> attribute, io.micronaut.data.annotation.Join.Type jt);

}
