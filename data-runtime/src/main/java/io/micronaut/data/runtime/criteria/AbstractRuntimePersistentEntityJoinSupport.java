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
package io.micronaut.data.runtime.criteria;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityJoinSupport;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.criteria.CriteriaBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Internal
abstract class AbstractRuntimePersistentEntityJoinSupport<T, E> extends AbstractPersistentEntityJoinSupport<T, E> {

    private final CriteriaBuilder criteriaBuilder;

    AbstractRuntimePersistentEntityJoinSupport(CriteriaBuilder criteriaBuilder) {
        this.criteriaBuilder = criteriaBuilder;
    }

    protected abstract List<Association> getCurrentPath();

    @Override
    public abstract RuntimePersistentEntity<E> getPersistentEntity();

    @Override
    protected <Y> PersistentAssociationPath<E, Y> createJoinAssociation(Association association,
                                                                        Join.Type associationJoinType,
                                                                        String alias) {
        Class<?> type = ((RuntimeAssociation<?>) association).getProperty().getType();
        RuntimeAssociation<E> runtimeAssociation = (RuntimeAssociation<E>) association;
        if (List.class.isAssignableFrom(type)) {
            return new RuntimePersistentListAssociationPath<>(this, runtimeAssociation, getCurrentPath(), associationJoinType, alias, criteriaBuilder);
        }
        if (Set.class.isAssignableFrom(type)) {
            return new RuntimePersistentSetAssociationPath<>(this, runtimeAssociation, getCurrentPath(), associationJoinType, alias, criteriaBuilder);
        }
        if (Collection.class.isAssignableFrom(type)) {
            return new RuntimePersistentCollectionAssociationPath<>(this, runtimeAssociation, getCurrentPath(), associationJoinType, alias, criteriaBuilder);
        }
        return new RuntimePersistentAssociationPath<>(this, runtimeAssociation, getCurrentPath(), associationJoinType, alias, criteriaBuilder);
    }

    @Override
    public <Y> PersistentPropertyPath<Y> get(String attributeName) {
        RuntimePersistentProperty<?> property = getPersistentEntity().getPropertyByName(attributeName);
        if (property == null) {
            throw new IllegalStateException("Cannot query entity [" + getPersistentEntity().getSimpleName() + "] on non-existent property: " + attributeName);
        }
        if (this instanceof PersistentAssociationPath<?, ?> associationPath) {
            List<Association> associations = associationPath.getAssociations();
            List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
            newAssociations.addAll(associations);
            newAssociations.add(associationPath.getAssociation());
            return new RuntimePersistentPropertyPathImpl<>(this, newAssociations, property, criteriaBuilder);
        }
        return new RuntimePersistentPropertyPathImpl<>(this, Collections.emptyList(), property, criteriaBuilder);
    }

}
