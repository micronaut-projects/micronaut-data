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
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Bindable;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The abstract implementation of {@link PersistentPropertyPath}.
 *
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
public class DefaultEmbeddedPersistentPropertyPath<T> implements PersistentPropertyPath<T> {

    private final io.micronaut.data.model.PersistentAssociationPath propertyPath;
    private final BiFunction<Path<?>, PersistentProperty, PersistentPropertyPath<?>> getPropertyFn;

    public DefaultEmbeddedPersistentPropertyPath(Association association,
        List<Association> associations,
        BiFunction<Path<?>, PersistentProperty, PersistentPropertyPath<?>> getPropertyFn) {
        this(new io.micronaut.data.model.PersistentAssociationPath(associations, association), getPropertyFn);
    }

    public DefaultEmbeddedPersistentPropertyPath(io.micronaut.data.model.PersistentAssociationPath propertyPath,
                                                 BiFunction<Path<?>, PersistentProperty, PersistentPropertyPath<?>> getPropertyFn) {
        this.propertyPath = propertyPath;
        this.getPropertyFn = getPropertyFn;
        if (propertyPath.getAssociation() == null) {
            throw new IllegalArgumentException("Embedded association path must have an association: " + propertyPath);
        }
        if (!propertyPath.getAssociation().isEmbedded()) {
            throw new IllegalArgumentException("Embedded association path must be have an embedded association: " + propertyPath);
        }
    }

    private IllegalStateException inNotSupported() {
        return new IllegalStateException("Embedded association doesn't support IN predicate");
    }

    @Override
    public Predicate in(Object... values) {
        throw inNotSupported();
    }

    @Override
    public Predicate in(Collection<?> values) {
        throw inNotSupported();
    }

    @Override
    public Predicate in(Expression<?>... values) {
        throw inNotSupported();
    }

    @Override
    public Predicate in(Expression<Collection<?>> values) {
        throw inNotSupported();
    }

    @Override
    public Association getProperty() {
        return propertyPath.getProperty();
    }

    @Override
    public List<Association> getAssociations() {
        return propertyPath.getAssociations();
    }

    @Override
    public io.micronaut.data.model.PersistentAssociationPath getPropertyPath() {
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
        Association association = propertyPath.getAssociation();
        PersistentProperty property = association.getAssociatedEntity().getPropertyByNameIgnoreCase(attributeName);
        if (property == null) {
            throw new IllegalArgumentException("Embedded association doesn't have a property with name: " + attributeName);
        }
        return (PersistentPropertyPath<Y>) getPropertyFn.apply(this, property);
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
