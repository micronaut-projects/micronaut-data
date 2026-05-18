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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.impl.expression.ClassExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.predicate.UnaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateUnaryOp;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * The persistent property {@link Path}.
 *
 * @param <T> The path type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentPropertyPath<T> extends Path<T>, IExpression<T> {

    PersistentProperty getProperty();

    List<Association> getAssociations();

    default io.micronaut.data.model.PersistentPropertyPath getPropertyPath() {
        return new io.micronaut.data.model.PersistentPropertyPath(getAssociations(), getProperty());
    }

    default String getPathAsString() {
        StringJoiner joiner = new StringJoiner(".");
        for (Association association : getAssociations()) {
            joiner.add(association.getName());
        }
        joiner.add(getProperty().getName());
        return joiner.toString();
    }

    @Override
    <Y> PersistentPropertyPath<Y> get(String attributeName);

    @Override
    default <Y> PersistentPropertyPath<Y> get(SingularAttribute<? super T, Y> attribute) {
        return get(attribute.getName());
    }

    @Override
    default <E, C extends Collection<E>> Expression<C> get(PluralAttribute<? super T, C, E> collection) {
        return get(collection.getName());
    }

    @Override
    default <K, V, M extends Map<K, V>> Expression<M> get(MapAttribute<? super T, K, V> map) {
        return get(map.getName());
    }

    @Override
    default ExpressionType<T> getExpressionType() {
        return (ExpressionType<T>) new ClassExpressionType<>(getJavaType());
    }

    @Override
    default Predicate isNull() {
        return new UnaryPredicate(this, PredicateUnaryOp.IS_NULL);
    }

    @Override
    default Predicate isNotNull() {
        return new UnaryPredicate(this, PredicateUnaryOp.IS_NON_NULL);
    }

}
