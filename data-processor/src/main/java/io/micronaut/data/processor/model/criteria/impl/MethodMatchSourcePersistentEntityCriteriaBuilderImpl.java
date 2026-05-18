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

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaInsert;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractCriteriaBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaDelete;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.Utils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.ParameterExpression;

import java.util.Map;

/**
 * The internal source implementation of {@link SourcePersistentEntityCriteriaBuilder} that supports mapping method parameters.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class MethodMatchSourcePersistentEntityCriteriaBuilderImpl extends AbstractCriteriaBuilder implements SourcePersistentEntityCriteriaBuilder {

    private final Map<String, DataType> dataTypes;
    private final MethodMatchContext methodMatchContext;

    public MethodMatchSourcePersistentEntityCriteriaBuilderImpl(MethodMatchContext matchContext) {
        this.methodMatchContext = matchContext;
        this.dataTypes = Utils.getConfiguredDataTypes(matchContext.getRepositoryClass());
    }

    @Override
    public PersistentEntityCriteriaQuery<Tuple> createTupleQuery() {
        return new SourcePersistentEntityCriteriaQueryImpl<>(Tuple.class, methodMatchContext::getEntity, this);
    }

    @Override
    public SourcePersistentEntityCriteriaQuery<Object> createQuery() {
        return new SourcePersistentEntityCriteriaQueryImpl<>(Object.class, methodMatchContext::getEntity, this);
    }

    @Override
    public <T> SourcePersistentEntityCriteriaQuery<T> createQuery(@Nullable Class<T> resultClass) {
        Class aClass = resultClass == null ? Object.class : resultClass;
        return new SourcePersistentEntityCriteriaQueryImpl<>(aClass, methodMatchContext::getEntity, this);
    }

    @Override
    public <T> SourcePersistentEntityCriteriaDelete<T> createCriteriaDelete(@Nullable Class<T> targetEntity) {
        return new SourcePersistentEntityCriteriaDeleteImpl<>(methodMatchContext::getEntity, targetEntity, this);
    }

    @Override
    public <T> SourcePersistentEntityCriteriaUpdate<T> createCriteriaUpdate(@Nullable Class<T> targetEntity) {
        return new SourcePersistentEntityCriteriaUpdateImpl<>(methodMatchContext::getEntity, targetEntity, this);
    }

    @Override
    public <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(Class<T> targetEntity) {
        return createCriteriaInsert(methodMatchContext.getVisitorContext().getClassElement(targetEntity).orElseThrow());
    }

    @Override
    public <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(ClassElement targetEntity) {
        return createCriteriaInsert(methodMatchContext.getEntity(targetEntity));
    }

    @Override
    public <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(SourcePersistentEntity targetEntity) {
        return new SourcePersistentEntityCriteriaInsertImpl<>(targetEntity, this);
    }

    @Override
    public ParameterExpression<Object> expression(PersistentProperty property, String expression) {
        return new SourceParameterStringExpressionImpl(property, expression);
    }

    @Override
    public ParameterExpression<Object> parameter(@Nullable ParameterElement parameterElement,
                                                 @Nullable PersistentPropertyPath propertyPath) {
        return new SourceParameterExpressionImpl(dataTypes, methodMatchContext.getParameters(), parameterElement, false, propertyPath);
    }

    @Override
    public ParameterExpression<Object> parameterReferencingMethodParameter(int parameterIndex) {
        return new SourceParameterExpressionImpl(dataTypes, methodMatchContext.getParameters(), methodMatchContext.getParameters()[parameterIndex], false, null);
    }

    @Override
    public ParameterExpression<Object> parameterReferencingMethodParameter(String parameterName) {
        ParameterElement parameterElement = null;
        ParameterElement[] parameters = methodMatchContext.getParameters();
        for (ParameterElement parameter : parameters) {
            if (parameter.stringValue(Parameter.class).orElse(parameter.getName()).equals(parameterName)) {
                parameterElement = parameter;
                break;
            }
        }
        return new SourceParameterExpressionImpl(dataTypes, methodMatchContext.getParameters(), parameterElement, false, null);
    }

    @Override
    public ParameterExpression<Object> entityPropertyParameter(@Nullable ParameterElement entityParameter,
                                                               @Nullable PersistentPropertyPath propertyPath) {
        return new SourceParameterExpressionImpl(dataTypes, methodMatchContext.getParameters(), entityParameter, true, propertyPath);
    }
}
