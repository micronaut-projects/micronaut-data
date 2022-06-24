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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInValuesPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyUnaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateUnaryOp;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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

    @NonNull
    PersistentProperty getProperty();

    @NonNull
    List<Association> getAssociations();

    @NonNull
    default String getPathAsString() {
        StringJoiner joiner = new StringJoiner(".");
        for (Association association : getAssociations()) {
            joiner.add(association.getName());
        }
        joiner.add(getProperty().getName());
        return joiner.toString();
    }

    @Override
    default boolean isBoolean() {
        return CriteriaUtils.isBoolean(getJavaType());
    }

    @Override
    default boolean isNumeric() {
        return CriteriaUtils.isNumeric(getJavaType());
    }

    @Override
    default boolean isComparable() {
        return CriteriaUtils.isComparable(getJavaType());
    }

    @Override
    default Predicate isNull() {
        return new PersistentPropertyUnaryPredicate<>(this, PredicateUnaryOp.IS_NULL);
    }

    @Override
    default Predicate isNotNull() {
        return new PersistentPropertyUnaryPredicate<>(this, PredicateUnaryOp.IS_NON_NULL);
    }

    @Override
    default Predicate in(Object... values) {
        return new PersistentPropertyInPredicate<>(this, Arrays.asList(Objects.requireNonNull(values)));
    }

    @Override
    default Predicate in(Collection<?> values) {
        return new PersistentPropertyInPredicate<>(this, Objects.requireNonNull(values));
    }

    @Override
    default Predicate in(Expression<?>... values) {
        return new PersistentPropertyInValuesPredicate<>(this, Arrays.asList(values));
    }

}
