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
package io.micronaut.data.model.jpa.criteria.impl.predicate;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The property IN value predicate implementation.
 *
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class PersistentPropertyInPredicate<T> extends AbstractPersistentPropertyPredicate<T> implements CriteriaBuilder.In<T> {

    private final List<Expression<?>> values;
    private final CriteriaBuilder criteriaBuilder;

    public PersistentPropertyInPredicate(PersistentPropertyPath<T> propertyPath, CriteriaBuilder criteriaBuilder) {
        this(propertyPath, Collections.emptyList(), criteriaBuilder);
    }

    public PersistentPropertyInPredicate(PersistentPropertyPath<T> propertyPath, Collection<Expression<?>> values, CriteriaBuilder criteriaBuilder) {
        super(propertyPath);
        this.values = new ArrayList<>(values);
        this.criteriaBuilder = criteriaBuilder;
    }

    @NonNull
    public List<Expression<?>> getValues() {
        return values;
    }

    @Override
    public Expression<T> getExpression() {
        return getPropertyPath();
    }

    @Override
    public PersistentPropertyInPredicate<T> value(T value) {
        values.add(criteriaBuilder.literal(value));
        return this;
    }

    @Override
    public PersistentPropertyInPredicate<T> value(Expression<? extends T> value) {
        values.add(value);
        return this;
    }

    @Override
    public void accept(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "PersistentPropertyInPredicate{" +
                "persistentPropertyPath=" + persistentPropertyPath +
                ", values=" + values +
                '}';
    }
}
