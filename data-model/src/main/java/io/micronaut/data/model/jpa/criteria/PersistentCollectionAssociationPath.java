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
import jakarta.persistence.criteria.CollectionJoin;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.CollectionAttribute;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The persistent entity association path.
 *
 * @param <OwnerType>            The association owner type
 * @param <AssociatedEntityType> The association entity type
 * @author Denis Stepanov
 * @since 3.5
 */
@Experimental
public interface PersistentCollectionAssociationPath<OwnerType, AssociatedEntityType>
    extends PersistentAssociationPath<OwnerType, AssociatedEntityType>, PersistentEntityCollectionJoin<OwnerType, AssociatedEntityType> {

    @Override
    default CollectionAttribute<? super OwnerType, AssociatedEntityType> getModel() {
        throw notSupportedOperation();
    }

    @Override
    default CollectionJoin<OwnerType, AssociatedEntityType> on(Predicate... restrictions) {
        throw notSupportedOperation();
    }

    @Override
    default CollectionJoin<OwnerType, AssociatedEntityType> on(Expression<Boolean> restriction) {
        throw notSupportedOperation();
    }
}
