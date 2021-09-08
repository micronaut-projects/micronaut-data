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
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate;
import io.micronaut.inject.ast.ClassElement;

import java.util.function.Function;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The internal source version of {@link SourcePersistentEntityCriteriaUpdate}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class SourcePersistentEntityCriteriaUpdateImpl<T> extends AbstractPersistentEntityCriteriaUpdate<T>
        implements SourcePersistentEntityCriteriaUpdate<T> {

    private final Function<ClassElement, SourcePersistentEntity> entityResolver;

    public SourcePersistentEntityCriteriaUpdateImpl(Function<ClassElement, SourcePersistentEntity> entityResolver, Class<T> root) {
        this.entityResolver = entityResolver;
    }

    @Override
    public PersistentEntityRoot<T> from(ClassElement entityClassElement) {
        return from(new SourcePersistentEntity(entityClassElement, entityResolver));
    }

    @Override
    public PersistentEntityRoot<T> from(Class<T> entityClass) {
        throw notSupportedOperation();
    }

    @Override
    public PersistentEntityRoot<T> from(PersistentEntity persistentEntity) {
        if (entityRoot != null) {
            throw new IllegalStateException("The root entity is already specified!");
        }
        SourcePersistentEntityRoot<T> newEntityRoot = new SourcePersistentEntityRoot<>((SourcePersistentEntity) persistentEntity);
        entityRoot = newEntityRoot;
        return newEntityRoot;
    }

    @Override
    protected void setValue(String attributeName, Object value) {
        if (value instanceof SourceParameterExpressionImpl) {
            ((SourceParameterExpressionImpl) value).setUpdate(true);
        }
        super.setValue(attributeName, value);
    }
}
