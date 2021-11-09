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
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.Collection;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The internal implementation of {@link Expression}.
 *
 * @param <T> The expression type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface IExpression<T> extends Expression<T>, ISelection<T> {

    /**
     * @return true if the expression is of boolean type
     */
    boolean isBoolean();

    /**
     * @return true if the expression is of numeric type
     */
    boolean isNumeric();

    @Override
    @NonNull
    default Predicate isNull() {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default Predicate isNotNull() {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default Predicate in(Object... values) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default Predicate in(Expression<?>... values) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default Predicate in(Collection<?> values) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default Predicate in(Expression<Collection<?>> values) {
        throw notSupportedOperation();
    }

    @Override
    @NonNull
    default <X> Expression<X> as(Class<X> type) {
        throw notSupportedOperation();
    }

}
