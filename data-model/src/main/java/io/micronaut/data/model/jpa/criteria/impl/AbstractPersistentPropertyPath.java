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
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The abstract implementation of {@link PersistentPropertyPath}.
 *
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPersistentPropertyPath<T> implements PersistentPropertyPath<T>, SelectionVisitable {

    private final PersistentProperty persistentProperty;
    private final List<Association> path;

    public AbstractPersistentPropertyPath(PersistentProperty persistentProperty, List<Association> path) {
        this.persistentProperty = persistentProperty;
        this.path = path;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    @Override
    public PersistentProperty getProperty() {
        return persistentProperty;
    }

    @Override
    public List<Association> getAssociations() {
        return path;
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
    public <E, C extends Collection<E>> Expression<C> get(PluralAttribute<T, C, E> collection) {
        throw notSupportedOperation();
    }

    @Override
    public <K, V, M extends Map<K, V>> Expression<M> get(MapAttribute<T, K, V> map) {
        throw notSupportedOperation();
    }

    @Override
    public <Y> Path<Y> get(SingularAttribute<? super T, Y> attribute) {
        throw notSupportedOperation();
    }

    @Override
    public Expression<Class<? extends T>> type() {
        throw notSupportedOperation();
    }

    @Override
    public <Y> Path<Y> get(String attributeName) {
        throw notSupportedOperation();
    }

    @Override
    public Class<? extends T> getJavaType() {
        throw notSupportedOperation();
    }

    @Override
    public String toString() {
        return "PersistentPropertyPath{" +
                "persistentProperty=" + persistentProperty +
                ", path=" + path +
                '}';
    }
}
