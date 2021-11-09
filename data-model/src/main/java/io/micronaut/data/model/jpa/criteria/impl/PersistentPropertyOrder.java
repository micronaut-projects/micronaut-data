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
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;

/**
 * The implementation of {@link Order}.
 *
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class PersistentPropertyOrder<T> implements Order {

    private final PersistentPropertyPath<T> persistentPropertyPath;
    private final boolean ascending;

    public PersistentPropertyOrder(PersistentPropertyPath<T> persistentPropertyPath, boolean ascending) {
        this.persistentPropertyPath = persistentPropertyPath;
        this.ascending = ascending;
    }

    @Override
    public Order reverse() {
        return new PersistentPropertyOrder<>(persistentPropertyPath, !ascending);
    }

    @Override
    public boolean isAscending() {
        return ascending;
    }

    @Override
    public Expression<?> getExpression() {
        return persistentPropertyPath;
    }
}
