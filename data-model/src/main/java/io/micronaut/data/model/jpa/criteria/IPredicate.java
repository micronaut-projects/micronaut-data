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
import jakarta.persistence.criteria.Predicate;

/**
 * The internal implementation of {@link IPredicate}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface IPredicate extends Predicate, IExpression<Boolean> {

    @Override
    default boolean isBoolean() {
        return true;
    }

    @Override
    default boolean isNumeric() {
        return false;
    }

    @Override
    default boolean isComparable() {
        return true;
    }
}
