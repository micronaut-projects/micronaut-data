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
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import jakarta.persistence.metamodel.EntityType;

import java.util.Collections;
import java.util.List;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The internal source version of {@link PersistentEntityRoot}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class SourcePersistentEntityRoot<T> extends AbstractSourcePersistentEntityJoinSupport<T, T>
        implements SourcePersistentEntityPath<T>, PersistentEntityRoot<T> {

    private final SourcePersistentEntity sourcePersistentEntity;

    public SourcePersistentEntityRoot(SourcePersistentEntity sourcePersistentEntity) {
        this.sourcePersistentEntity = sourcePersistentEntity;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    @Override
    public SourcePersistentEntity getPersistentEntity() {
        return sourcePersistentEntity;
    }

    @Override
    public List<Association> getAssociations() {
        return Collections.emptyList();
    }

    @Override
    protected List<Association> getCurrentPath() {
        return Collections.emptyList();
    }

    @Override
    public EntityType<T> getModel() {
        throw notSupportedOperation();
    }

    @Override
    public boolean isBoolean() {
        return false;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isComparable() {
        return false;
    }

    @Override
    public String toString() {
        return "SourcePersistentEntityRoot{" +
                "sourcePersistentEntity=" + sourcePersistentEntity +
                '}';
    }
}
