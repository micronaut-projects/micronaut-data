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
package io.micronaut.data.processor.model.criteria;

import io.micronaut.core.annotation.Experimental;

import org.jspecify.annotations.Nullable;

import io.micronaut.data.model.PersistentProperty;

import io.micronaut.data.model.PersistentPropertyPath;

import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;

import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaInsert;

import io.micronaut.data.processor.model.SourcePersistentEntity;

import io.micronaut.inject.ast.ClassElement;

import io.micronaut.inject.ast.ParameterElement;

import jakarta.persistence.criteria.ParameterExpression;

/**
 * The source persistent entity extension of {@link PersistentEntityCriteriaBuilder}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface SourcePersistentEntityCriteriaBuilder extends PersistentEntityCriteriaBuilder {

/**
     * Create parameter expression from {@link ParameterElement}.
     *
     * @param property   The property
     * @param expression The expression
     * @param <T>        The expression type
     * @return new parameter
     */
    <T> ParameterExpression<T> expression(PersistentProperty property, String expression);

/**
     * Create parameter expression from {@link ParameterElement}.
     *
     * @param parameterElement The parameter element
     * @param propertyPath     The property path this parameter is representing
     * @param <T>              The expression type
     * @return new parameter
     */
    <T> ParameterExpression<T> parameter(@Nullable ParameterElement parameterElement,
                                         @Nullable PersistentPropertyPath propertyPath);

/**
     * Create parameter expression from {@link ParameterElement}.
     *
     * @param parameterIndex The parameter index
     * @param <T>            The expression type
     * @return new parameter
     * @since 4.13
     */
    <T> ParameterExpression<T> parameterReferencingMethodParameter(int parameterIndex);

/**
     * Create parameter expression from {@link ParameterElement}.
     *
     * @param parameterName The parameter name
     * @param <T>           The expression type
     * @return new parameter
     * @since 4.13
     */
    <T> ParameterExpression<T> parameterReferencingMethodParameter(String parameterName);

/**
     * Create parameter expression from {@link ParameterElement} that is representing an entity instance.
     *
     * @param entityParameter The entity parameter element
     * @param propertyPath    The property path this parameter is representing
     * @param <T>             The expression type
     * @return new parameter
     */
    <T> ParameterExpression<T> entityPropertyParameter(@Nullable ParameterElement entityParameter,
                                                       @Nullable PersistentPropertyPath propertyPath);

    @Override
    <T> SourcePersistentEntityCriteriaDelete<T> createCriteriaDelete(@Nullable Class<T> targetEntity);

    @Override
    <T> SourcePersistentEntityCriteriaUpdate<T> createCriteriaUpdate(@Nullable Class<T> targetEntity);

/**
     * The criteria insert.
     * @param targetEntity The target entity
     * @param <T> The type
     * @return The criteria insert
     */
    <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(ClassElement targetEntity);

/**
     * The criteria insert.
     * @param targetEntity The target entity
     * @param <T> The type
     * @return The criteria insert
     */
    <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(SourcePersistentEntity targetEntity);

    @Override
    SourcePersistentEntityCriteriaQuery<Object> createQuery();

    @Override
    <T> SourcePersistentEntityCriteriaQuery<T> createQuery(@Nullable Class<T> resultClass);

}
