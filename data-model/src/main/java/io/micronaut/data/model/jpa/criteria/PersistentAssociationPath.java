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
import io.micronaut.data.model.Association;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;

import java.util.ArrayList;
import java.util.List;

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
        PersistentPropertyPath<AssociatedEntityType> {

    @NonNull
    @Override
    Association getProperty();

    @NonNull
    Association getAssociation();

    @NonNull
    default List<Association> asPath() {
        List<Association> associations = getAssociations();
        List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
        newAssociations.addAll(associations);
        newAssociations.add(getAssociation());
        return newAssociations;
    }

    @Override
    @NonNull
    default Join<OwnerType, AssociatedEntityType> on(Expression<Boolean> restriction) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default Join<OwnerType, AssociatedEntityType> on(Predicate... restrictions) {
        throw notSupportedOperation();
    }

    @Override
    @Nullable
    default Predicate getOn() {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default Attribute<? super OwnerType, ?> getAttribute() {
        throw notSupportedOperation();
    }

    @Override
    @Nullable
    default From<?, OwnerType> getParent() {
        return null;
    }

    @Override
    @NonNull
    default JoinType getJoinType() {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default Bindable<AssociatedEntityType> getModel() {
        throw notSupportedOperation();
    }

}
