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
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.CastExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.ClassExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Bindable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The abstract implementation of {@link PersistentPropertyPath}.
 *
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class DefaultPersistentPropertyPath<T> implements PersistentPropertyPath<T> {

    private final io.micronaut.data.model.PersistentPropertyPath propertyPath;
    private final CriteriaBuilder criteriaBuilder;

    public DefaultPersistentPropertyPath(PersistentProperty persistentProperty, List<Association> associations, CriteriaBuilder criteriaBuilder) {
        this(new io.micronaut.data.model.PersistentPropertyPath(associations, persistentProperty), criteriaBuilder);
    }

    public DefaultPersistentPropertyPath(io.micronaut.data.model.PersistentPropertyPath propertyPath, CriteriaBuilder criteriaBuilder) {
        this.propertyPath = propertyPath;
        this.criteriaBuilder = criteriaBuilder;
    }

    @SuppressWarnings("NullAway")
    public DefaultPersistentPropertyPath(PersistentProperty persistentProperty, List<Association> associations) {
        this(new io.micronaut.data.model.PersistentPropertyPath(associations, persistentProperty), null);
    }

    @SuppressWarnings("NullAway")
    public DefaultPersistentPropertyPath(io.micronaut.data.model.PersistentPropertyPath propertyPath) {
        this.propertyPath = propertyPath;
        this.criteriaBuilder = null;
    }

    @Override
    public <X> Expression<X> cast(Class<X> type) {
        return new CastExpression<>(this, new ClassExpressionType<>(type));
    }

    @Override
    public Predicate in(Object... values) {
        return in(Arrays.asList(Objects.requireNonNull(values)));
    }

    @Override
    public Predicate in(Collection<?> values) {
        List<Expression<?>> expressions = Objects.requireNonNull(values).stream().map(value -> {
            if (value instanceof Expression<?> expression) {
                return expression;
            }
            return criteriaBuilder.literal(value);
        }).collect(Collectors.toList());
        return new InPredicate<>(this, expressions, criteriaBuilder);
    }

    @Override
    public Predicate in(Expression<?>... values) {
        return new InPredicate<>(this, Arrays.asList(values), criteriaBuilder);
    }

    @Override
    public Predicate in(Expression<Collection<?>> values) {
        return new InPredicate<>(this, List.of(Objects.requireNonNull(values)), criteriaBuilder);
    }

    @Override
    public PersistentProperty getProperty() {
        return propertyPath.getProperty();
    }

    @Override
    public List<Association> getAssociations() {
        return propertyPath.getAssociations();
    }

    @Override
    public io.micronaut.data.model.PersistentPropertyPath getPropertyPath() {
        return propertyPath;
    }

    @Override
    public Bindable<T> getModel() {
        throw notSupportedOperation();
    }

    @Override
    public Path<?> getParentPath() {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Class<? extends T>> type() {
        throw notSupportedOperation();
    }

    @Override
    public <Y> PersistentPropertyPath<Y> get(String attributeName) {
        if (propertyPath.getProperty() instanceof Association) {
            throw new IllegalStateException("An association: " + Arrays.toString(propertyPath.getArrayPath()) + " needs to be joined before it can be accessed");
        }
        throw new IllegalArgumentException("Property path doesn't support get operation: " + Arrays.toString(propertyPath.getArrayPath()));
    }

    @Override
    public Class<? extends T> getJavaType() {
        throw notSupportedOperation();
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "PersistentPropertyPath{" + propertyPath + '}';
    }
}
