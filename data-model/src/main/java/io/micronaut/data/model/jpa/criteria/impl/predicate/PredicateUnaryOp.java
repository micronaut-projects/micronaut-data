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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import jakarta.persistence.criteria.Expression;

/**
 * Predicate unary operations.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public enum PredicateUnaryOp {
    IS_NULL,
    IS_NON_NULL,
    IS_TRUE,
    IS_FALSE,
    IS_EMPTY,
    IS_NOT_EMPTY;

    void validate(@NonNull Expression<?> expression) {
        switch (this) {
            case IS_EMPTY, IS_NOT_EMPTY -> {
                CriteriaUtils.requireStringExpression(expression);
            }
            case IS_TRUE, IS_FALSE -> {
                // Boolean?
            }
            case IS_NULL, IS_NON_NULL -> {
                // Any type
            }
            default -> throw new IllegalStateException("Unsupported predicate: " + this);
        }
    }

    @Nullable
    public PredicateUnaryOp negate() {
        return switch (this) {
            case IS_NULL -> IS_NON_NULL;
            case IS_NON_NULL -> IS_NULL;
            case IS_TRUE -> IS_FALSE;
            case IS_FALSE -> IS_TRUE;
            case IS_EMPTY -> IS_NOT_EMPTY;
            case IS_NOT_EMPTY -> IS_EMPTY;
        };
    }
}
