/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaInsert;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import jakarta.persistence.criteria.CriteriaBuilder;

/**
 * The internal source version of the insert criteria.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class SourcePersistentEntityCriteriaInsertImpl<T> extends AbstractPersistentEntityCriteriaInsert<T> {

    private final CriteriaBuilder criteriaBuilder;

    SourcePersistentEntityCriteriaInsertImpl(SourcePersistentEntity root,
                                             CriteriaBuilder criteriaBuilder) {
        this.criteriaBuilder = criteriaBuilder;
        from(root);
    }

    private PersistentEntityRoot<T> from(SourcePersistentEntity persistentEntity) {
        if (entityRoot != null) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        SourcePersistentEntityRoot<T> newEntityRoot = new SourcePersistentEntityRoot<>(persistentEntity, criteriaBuilder);
        entityRoot = newEntityRoot;
        return newEntityRoot;
    }
}
