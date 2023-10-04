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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitable;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.inject.ast.ClassElement;

import java.util.function.Function;

/**
 * The internal source implementation of {@link SourcePersistentEntityCriteriaQuery}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class SourcePersistentEntityCriteriaQueryImpl<T> extends AbstractPersistentEntityCriteriaQuery<T>
        implements SourcePersistentEntityCriteriaQuery<T> {

    private final Function<ClassElement, SourcePersistentEntity> entityResolver;

    public SourcePersistentEntityCriteriaQueryImpl(Function<ClassElement, SourcePersistentEntity> entityResolver) {
        super((Class<T>) Object.class);
        this.entityResolver = entityResolver;
    }

    @Override
    public <X> PersistentEntityRoot<X> from(ClassElement entityClassElement) {
        return from(new SourcePersistentEntity(entityClassElement, entityResolver));
    }

    @Override
    public <X> PersistentEntityRoot<X> from(Class<X> entityClass) {
        throw new IllegalStateException("Unsupported operation");
    }

    @Override
    public <X> PersistentEntityRoot<X> from(PersistentEntity persistentEntity) {
        if (entityRoot != null) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        SourcePersistentEntityRoot<X> newEntityRoot = new SourcePersistentEntityRoot<>((SourcePersistentEntity) persistentEntity);
        entityRoot = newEntityRoot;
        return newEntityRoot;
    }

    @Override
    public String getQueryResultTypeName() {
        if (selection instanceof SelectionVisitable selectionVisitable) {
            QueryResultAnalyzer selectionVisitor = new QueryResultAnalyzer();
            selectionVisitable.accept(selectionVisitor);
            return selectionVisitor.getQueryResultTypeName();
        }
        return null;
    }

}
