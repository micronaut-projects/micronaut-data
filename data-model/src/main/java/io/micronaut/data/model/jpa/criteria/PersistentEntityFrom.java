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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

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

    @Override
    @Nullable
    <X, Y> PersistentEntityJoin<X, Y> join(@NonNull String attributeName);

    /**
     * Joins the entity with specific join type.
     *
     * @param attributeName The joined associated property
     * @param joinType      The join type
     * @param <X>           The association owner type
     * @param <Y>           The association entity type
     * @return The joined entity
     */
    @NonNull
    <X, Y> PersistentEntityJoin<X, Y> join(@NonNull String attributeName, @NonNull io.micronaut.data.annotation.Join.Type joinType);

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
    @NonNull
    <X, Y> PersistentEntityJoin<X, Y> join(@NonNull String attributeName, @NonNull io.micronaut.data.annotation.Join.Type joinType, @NonNull String alias);

    @NonNull
    @Override
    <X, Y> PersistentEntityJoin<X, Y> join(@NonNull String attributeName, @NonNull JoinType jt);

    @NonNull
    @Override
    <Y> PersistentEntityJoin<AssociatedEntityType, Y> join(@NonNull SingularAttribute<? super AssociatedEntityType, Y> attribute, @NonNull JoinType jt);

    @NonNull
    @Override
    <Y> PersistentEntityJoin<AssociatedEntityType, Y> join(@NonNull SingularAttribute<? super AssociatedEntityType, Y> attribute);

    @NonNull
    @Override
    <Y> PersistentEntityCollectionJoin<AssociatedEntityType, Y> join(@NonNull CollectionAttribute<? super AssociatedEntityType, Y> collection, @NonNull JoinType jt);

    @NonNull
    @Override
    <Y> PersistentEntityCollectionJoin<AssociatedEntityType, Y> join(@NonNull CollectionAttribute<? super AssociatedEntityType, Y> collection);

    @NonNull
    @Override
    <Y> PersistentEntityListJoin<AssociatedEntityType, Y> join(@NonNull ListAttribute<? super AssociatedEntityType, Y> list);

    @NonNull
    @Override
    <Y> PersistentEntityListJoin<AssociatedEntityType, Y> join(@NonNull ListAttribute<? super AssociatedEntityType, Y> list, @NonNull JoinType jt);

    @NonNull
    @Override
    <X, Y> PersistentEntityListJoin<X, Y> joinList(@NonNull String attributeName);

    @NonNull
    @Override
    <X, Y> PersistentEntityListJoin<X, Y> joinList(@NonNull String attributeName, @NonNull JoinType jt);

    @NonNull
    @Override
    <X, Y> PersistentEntityCollectionJoin<X, Y> joinCollection(@NonNull String attributeName);

    @NonNull
    @Override
    <X, Y> PersistentEntityCollectionJoin<X, Y> joinCollection(@NonNull String attributeName, @NonNull JoinType jt);

    @NonNull
    @Override
    <Y> PersistentEntitySetJoin<AssociatedEntityType, Y> join(@NonNull SetAttribute<? super AssociatedEntityType, Y> set);

    @NonNull
    @Override
    <Y> PersistentEntitySetJoin<AssociatedEntityType, Y> join(@NonNull SetAttribute<? super AssociatedEntityType, Y> set, @NonNull JoinType jt);

    @NonNull
    @Override
    <X, Y> PersistentEntitySetJoin<X, Y> joinSet(@NonNull String attributeName);

    @NonNull
    @Override
    <X, Y> PersistentEntitySetJoin<X, Y> joinSet(@NonNull String attributeName, @NonNull JoinType jt);

}
