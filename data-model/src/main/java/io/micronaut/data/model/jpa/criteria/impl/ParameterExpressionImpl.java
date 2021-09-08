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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.BindingParameter;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

import java.util.Collection;
import java.util.List;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The abstract implementation of {@link ParameterExpression}.
 *
 * @param <T> The parameter type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class ParameterExpressionImpl<T> implements ParameterExpression<T>, BindingParameter {

    private final Class<T> type;
    private final String name;

    public ParameterExpressionImpl(Class<T> type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Integer getPosition() {
        return null;
    }

    @Override
    public Class<T> getParameterType() {
        return type;
    }

    @Override
    public Predicate isNull() {
        throw notSupportedOperation();
    }

    @Override
    public Predicate isNotNull() {
        throw notSupportedOperation();
    }

    @Override
    public Predicate in(Object... values) {
        throw notSupportedOperation();
    }

    @Override
    public Predicate in(Expression<?>... values) {
        throw notSupportedOperation();
    }

    @Override
    public Predicate in(Collection<?> values) {
        throw notSupportedOperation();
    }

    @Override
    public Predicate in(Expression<Collection<?>> values) {
        throw notSupportedOperation();
    }

    @Override
    public <X> Expression<X> as(Class<X> type) {
        throw notSupportedOperation();
    }

    @Override
    public Selection<T> alias(String name) {
        throw notSupportedOperation();
    }

    @Override
    public boolean isCompoundSelection() {
        throw notSupportedOperation();
    }

    @Override
    public List<Selection<?>> getCompoundSelectionItems() {
        throw notSupportedOperation();
    }

    @Override
    public Class<? extends T> getJavaType() {
        return getParameterType();
    }

    @Override
    public String getAlias() {
        throw notSupportedOperation();
    }

    @Override
    public String toString() {
        return "ParameterExpressionImpl{" +
                "type=" + type +
                ", name='" + name + '\'' +
                '}';
    }
}
