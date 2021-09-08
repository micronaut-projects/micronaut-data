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
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaDelete;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.ParameterExpression;

import java.util.function.Function;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The source implementation of {@link SourcePersistentEntityCriteriaBuilder}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class SourcePersistentEntityCriteriaBuilderImpl extends AbstractCriteriaBuilder implements SourcePersistentEntityCriteriaBuilder {

    private final Function<ClassElement, SourcePersistentEntity> entityResolver;

    public SourcePersistentEntityCriteriaBuilderImpl(Function<ClassElement, SourcePersistentEntity> entityResolver) {
        this.entityResolver = entityResolver;
    }

    @Override
    public SourcePersistentEntityCriteriaQuery<Object> createQuery() {
        return new SourcePersistentEntityCriteriaQueryImpl<>(entityResolver);
    }

    @Override
    public <T> PersistentEntityCriteriaQuery<T> createQuery(Class<T> resultClass) {
        return new SourcePersistentEntityCriteriaQueryImpl<>(entityResolver);
    }

    @Override
    public <T> SourcePersistentEntityCriteriaDelete<T> createCriteriaDelete(Class<T> targetEntity) {
        return new SourcePersistentEntityCriteriaDeleteImpl<>(entityResolver, targetEntity);
    }

    @Override
    public <T> SourcePersistentEntityCriteriaUpdate<T> createCriteriaUpdate(Class<T> targetEntity) {
        return new SourcePersistentEntityCriteriaUpdateImpl<>(entityResolver, targetEntity);
    }

    @Override
    public ParameterExpression<Object> parameter(ParameterElement parameterElement) {
        throw notSupportedOperation();
    }

    @Override
    public ParameterExpression<Object> entityPropertyParameter(ParameterElement entityParameter) {
        throw notSupportedOperation();
    }
}
