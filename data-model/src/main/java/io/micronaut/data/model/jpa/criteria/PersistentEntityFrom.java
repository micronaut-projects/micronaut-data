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
import io.micronaut.data.annotation.Join;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.Collection;

/**
 * The persistent entity {@link From}.
 *
 * @param <OwnerType>            The association owner type
 * @param <AssociatedEntityType> The association entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentEntityFrom<OwnerType, AssociatedEntityType> extends From<OwnerType, AssociatedEntityType>, PersistentEntityPath<AssociatedEntityType> {

    /**
     * @return The persistent joins
     */
    Collection<PersistentAssociationPath<AssociatedEntityType, ?>> getPersistentJoins();

    @Override
    <X, Y> PersistentEntityJoin<X, Y> join(String attributeName);

    /**
     * Joins the entity with specific join type.
     *
     * @param attributeName The joined associated property
     * @param joinType      The join type
     * @param <X>           The association owner type
     * @param <Y>           The association entity type
     * @return The joined entity
     */
    <X, Y> PersistentEntityJoin<X, Y> join(String attributeName, Join.Type joinType);

    /**
     * Joins the entity with specific join type.
     *
     * @param attributeName The joined associated property
     * @param joinType      The join type
     * @param alias         The join alias
     * @param <X>           The association owner type
     * @param <Y>           The association entity type
     * @return The joined entity
     */
    <X, Y> PersistentEntityJoin<X, Y> join(String attributeName, Join.Type joinType,  String alias);

    @Override
    <X, Y> PersistentEntityJoin<X, Y> join(String attributeName,  JoinType jt);

    @Override
    <Y> PersistentEntityJoin<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute,  JoinType jt);

    /**
     * Joins the entity with specific join type.
     *
     * @param attribute The attribute
     * @param jt        The join type
     * @param <Y>       The association entity type
     * @return The joined entity
     */
    <Y> PersistentEntityJoin<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute, Join.Type jt);

    @Override
    <Y> PersistentEntityJoin<AssociatedEntityType, Y> join(SingularAttribute<? super AssociatedEntityType, Y> attribute);

    @Override
    <Y> PersistentEntityCollectionJoin<AssociatedEntityType, Y> join(CollectionAttribute<? super AssociatedEntityType, Y> collection,  JoinType jt);

    @Override
    <Y> PersistentEntityCollectionJoin<AssociatedEntityType, Y> join(CollectionAttribute<? super AssociatedEntityType, Y> collection);

    @Override
    <Y> PersistentEntityListJoin<AssociatedEntityType, Y> join(ListAttribute<? super AssociatedEntityType, Y> list);

    @Override
    <Y> PersistentEntityListJoin<AssociatedEntityType, Y> join(ListAttribute<? super AssociatedEntityType, Y> list,  JoinType jt);

    @Override
    <X, Y> PersistentEntityListJoin<X, Y> joinList(String attributeName);

    @Override
    <X, Y> PersistentEntityListJoin<X, Y> joinList(String attributeName,  JoinType jt);

    @Override
    <X, Y> PersistentEntityCollectionJoin<X, Y> joinCollection(String attributeName);

    @Override
    <X, Y> PersistentEntityCollectionJoin<X, Y> joinCollection(String attributeName,  JoinType jt);

    @Override
    <Y> PersistentEntitySetJoin<AssociatedEntityType, Y> join(SetAttribute<? super AssociatedEntityType, Y> set);

    @Override
    <Y> PersistentEntitySetJoin<AssociatedEntityType, Y> join(SetAttribute<? super AssociatedEntityType, Y> set,  JoinType jt);

    @Override
    <X, Y> PersistentEntitySetJoin<X, Y> joinSet(String attributeName);

    @Override
    <X, Y> PersistentEntitySetJoin<X, Y> joinSet(String attributeName,  JoinType jt);

}
