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
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaInsert;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaDelete;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.ParameterExpression;
import org.jspecify.annotations.Nullable;

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
        return new SourcePersistentEntityCriteriaQueryImpl<>(Object.class, entityResolver, this);
    }

    @Override
    public <T> SourcePersistentEntityCriteriaQuery<T> createQuery(@Nullable Class<T> resultClass) {
        Class aClass = resultClass == null ? Object.class : resultClass;
        return new SourcePersistentEntityCriteriaQueryImpl<>(aClass, entityResolver, this);
    }

    @Override
    public SourcePersistentEntityCriteriaQuery<Tuple> createTupleQuery() {
        return new SourcePersistentEntityCriteriaQueryImpl<>(Tuple.class, entityResolver, this);
    }

    @Override
    public <T> SourcePersistentEntityCriteriaDelete<T> createCriteriaDelete(@Nullable Class<T> targetEntity) {
        return new SourcePersistentEntityCriteriaDeleteImpl<>(entityResolver, targetEntity, this);
    }

    @Override
    public <T> SourcePersistentEntityCriteriaUpdate<T> createCriteriaUpdate(@Nullable Class<T> targetEntity) {
        return new SourcePersistentEntityCriteriaUpdateImpl<>(entityResolver, targetEntity, this);
    }

    @Override
    public <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(Class<T> targetEntity) {
        throw new UnsupportedOperationException("This operation is not yet supported.");
    }

    @Override
    public <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(ClassElement targetEntity) {
        return createCriteriaInsert(entityResolver.apply(targetEntity));
    }

    @Override
    public <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(SourcePersistentEntity targetEntity) {
        return new SourcePersistentEntityCriteriaInsertImpl<>(targetEntity, this);
    }

    @Override
    public <T> ParameterExpression<T> expression(PersistentProperty property, String expression) {
        throw notSupportedOperation();
    }

    @Override
    public <T> ParameterExpression<T> parameter(@Nullable ParameterElement parameterElement, @Nullable PersistentPropertyPath propertyPath) {
        throw notSupportedOperation();
    }

    @Override
    public <T> ParameterExpression<T> parameterReferencingMethodParameter(int parameterIndex) {
        return (ParameterExpression<T>) parameter(Object.class, "p" + parameterIndex);
    }

    @Override
    public <T> ParameterExpression<T> parameterReferencingMethodParameter(String parameterName) {
        return (ParameterExpression<T>) parameter(Object.class, parameterName);
    }

    @Override
    public <T> ParameterExpression<T> entityPropertyParameter(@Nullable ParameterElement entityParameter, @Nullable PersistentPropertyPath propertyPath) {
        throw notSupportedOperation();
    }
}
