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

import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityPath;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

interface RuntimePersistentEntityPath<T> extends PersistentEntityPath<T> {

    @Override
    RuntimePersistentEntity<T> getPersistentEntity();

    @Override
    default <Y> PersistentPropertyPath<Y> get(String attributeName) {
        RuntimePersistentProperty<?> property = getPersistentEntity().getPropertyByName(attributeName);
        if (property == null) {
            throw new IllegalStateException("Cannot query entity [" + getPersistentEntity().getSimpleName() + "] on non-existent property: " + attributeName);
        }
        if (this instanceof PersistentAssociationPath) {
            PersistentAssociationPath<?, ?> associationPath = (PersistentAssociationPath) this;
            List<Association> associations = associationPath.getAssociations();
            List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
            newAssociations.addAll(associations);
            newAssociations.add(associationPath.getAssociation());
            return new RuntimePersistentPropertyPathImpl<>(this, newAssociations, property);
        }
        return new RuntimePersistentPropertyPathImpl<>(this, Collections.emptyList(), property);
    }

}
