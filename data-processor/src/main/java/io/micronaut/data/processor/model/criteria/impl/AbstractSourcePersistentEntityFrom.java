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
package io.micronaut.data.processor.model.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.impl.DefaultEmbeddedPersistentPropertyPath;
import io.micronaut.data.processor.model.SourceAssociation;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The internal source implementation of {@link AbstractPersistentEntityFrom}.
 *
 * @param <T> The association entity type
 * @param <E> The association entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
abstract class AbstractSourcePersistentEntityFrom<T, E> extends AbstractPersistentEntityFrom<T, E> {

    protected final CriteriaBuilder criteriaBuilder;

    AbstractSourcePersistentEntityFrom(CriteriaBuilder criteriaBuilder) {
        this.criteriaBuilder = criteriaBuilder;
    }

    protected abstract List<Association> getCurrentPath();

    @Override
    public abstract SourcePersistentEntity getPersistentEntity();

    @Override
    public <Y> SourcePersistentPropertyPath<Y> get(String attributeName) {
        SourcePersistentProperty property = getPersistentEntity().getPropertyByNameIgnoreCase(attributeName);
        if (property == null) {
            throw new IllegalStateException("Cannot query entity [" + getPersistentEntity().getSimpleName() + "] on non-existent property: " + attributeName);
        }
        return asPropertyPath(this, property, criteriaBuilder);
    }

    private static <Y> SourcePersistentPropertyPath<Y> asPropertyPath(Path<?> parentPath,
                                                                      SourcePersistentProperty property,
                                                                      CriteriaBuilder criteriaBuilder) {
        List<Association> associations;
        if (parentPath instanceof PersistentAssociationPath<?, ?> associationPath) {
            List<Association> pathAssociations = associationPath.getAssociations();
            associations = new ArrayList<>(pathAssociations.size() + 1);
            associations.addAll(pathAssociations);
            associations.add(associationPath.getAssociation());
        } else if (parentPath instanceof DefaultEmbeddedPersistentPropertyPath<?> embedded) {
            associations = CollectionUtils.concat(embedded.getAssociations(), embedded.getProperty());
        } else {
            associations = List.of();
        }
        if (property instanceof SourceAssociation sourceAssociation && sourceAssociation.isEmbedded()) {
            return new SourceEmbeddedPersistentPropertyPathImpl<>(
                parentPath,
                associations,
                sourceAssociation,
                (path, persistentProperty) -> asPropertyPath(path, (SourcePersistentProperty) persistentProperty, criteriaBuilder)
            );
        }
        return new SourcePersistentPropertyPathImpl<>(parentPath, associations, property, criteriaBuilder);
    }

    @Override
    protected <Y> PersistentAssociationPath<E, Y> createJoinAssociation(Association association,
                                                                        io.micronaut.data.annotation.Join. @Nullable Type associationJoinType,
                                                                        @Nullable String alias) {
        return new SourcePersistentAssociationPath<>(this,
            (SourceAssociation) association,
            getCurrentPath(),
            associationJoinType,
            alias,
            criteriaBuilder
        );
    }

}
