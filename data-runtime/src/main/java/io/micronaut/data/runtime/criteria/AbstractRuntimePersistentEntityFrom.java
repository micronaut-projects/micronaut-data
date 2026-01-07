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
import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityFrom;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Internal
abstract sealed class AbstractRuntimePersistentEntityFrom<T, E> extends AbstractPersistentEntityFrom<T, E> permits RuntimePersistentAssociationPath, RuntimePersistentEntityRoot {

    private final CriteriaBuilder criteriaBuilder;

    AbstractRuntimePersistentEntityFrom(CriteriaBuilder criteriaBuilder) {
        this.criteriaBuilder = criteriaBuilder;
    }

    protected abstract List<Association> getCurrentPath();

    @Override
    public abstract RuntimePersistentEntity<E> getPersistentEntity();

    @Override
    protected <Y> PersistentAssociationPath<E, Y> createJoinAssociation(Association association,
                                                                        Join. @Nullable Type associationJoinType,
                                                                        @Nullable String alias) {
        RuntimeAssociation<E> runtimeAssociation = (RuntimeAssociation<E>) association;
        Class<?> type = runtimeAssociation.getProperty().getType();
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
        for (PersistentAssociationPath<E, ?> persistentJoin : getPersistentJoins()) {
            if (persistentJoin.getProperty().getName().equalsIgnoreCase(attributeName)) {
                return (PersistentPropertyPath<Y>) persistentJoin;
            }
        }
        RuntimePersistentProperty<?> property = getPersistentEntity().getPropertyByNameIgnoreCase(attributeName);
        if (property == null) {
            throw new IllegalStateException("Cannot query entity [" + getPersistentEntity().getSimpleName() + "] on non-existent property: " + attributeName);
        }
        return asPropertyPath(this, property, criteriaBuilder);
    }

    private static <Y> PersistentPropertyPath<Y> asPropertyPath(Path<?> parentPath,
                                                                @NonNull RuntimePersistentProperty<?> property,
                                                                CriteriaBuilder criteriaBuilder) {
        List<Association> associations;
        if (parentPath instanceof PersistentPropertyPath<?> persistentPropertyPath && persistentPropertyPath.getProperty() instanceof Association association) {
            List<Association> pathAssociations = persistentPropertyPath.getAssociations();
            associations = new ArrayList<>(pathAssociations.size() + 1);
            associations.addAll(pathAssociations);
            associations.add(association);
        } else {
            associations = List.of();
        }
        if (property instanceof RuntimeAssociation<?> association) {
            if (association.isEmbedded()) {
                return new RuntimeEmbeddedPersistentPropertyPathImpl<>(
                    parentPath,
                    associations,
                    (RuntimeAssociation<Y>) association,
                    (path, persistentProperty) -> asPropertyPath(path, (RuntimePersistentProperty<?>) persistentProperty, criteriaBuilder)
                );
            }
            // Not joined association is being accessed
            // We might want to have an implementation that will fail if foreign property is accessed
        }
        return new RuntimePersistentPropertyPathImpl<>(parentPath, associations, property, criteriaBuilder);
    }

}
