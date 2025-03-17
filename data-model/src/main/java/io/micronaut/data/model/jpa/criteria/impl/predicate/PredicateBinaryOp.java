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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import jakarta.persistence.criteria.Expression;

/**
 * Predicate binary operations.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public enum PredicateBinaryOp {
    EQUALS,
    NOT_EQUALS,
    EQUALS_IGNORE_CASE,
    NOT_EQUALS_IGNORE_CASE,
    GREATER_THAN,
    GREATER_THAN_OR_EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUALS,
    REGEX,
    CONTAINS,
    CONTAINS_IGNORE_CASE,
    STARTS_WITH,
    ENDS_WITH,
    STARTS_WITH_IGNORE_CASE,
    ENDS_WITH_IGNORE_CASE,
    ARRAY_CONTAINS,
    ;

    void validate(@Nullable Expression<?> left, @Nullable Expression<?> right) {
        switch (this) {
            case EQUALS, NOT_EQUALS, EQUALS_IGNORE_CASE, NOT_EQUALS_IGNORE_CASE -> {
                // Any type
            }
            case GREATER_THAN, GREATER_THAN_OR_EQUALS, LESS_THAN, LESS_THAN_OR_EQUALS -> {
                CriteriaUtils.requireComparableExpression(left);
                CriteriaUtils.requireComparableExpression(right);
            }
            case REGEX, CONTAINS, CONTAINS_IGNORE_CASE, STARTS_WITH, ENDS_WITH, STARTS_WITH_IGNORE_CASE, ENDS_WITH_IGNORE_CASE -> {
                CriteriaUtils.requireStringExpression(left);
                CriteriaUtils.requireStringExpression(right);
            }

            case ARRAY_CONTAINS -> {
                // Is array?
            }
            default -> {
                throw new IllegalStateException("Unsupported predicate: " + this);
            }
        }
    }

    @Nullable
    public PredicateBinaryOp negate() {
        return switch (this) {
            case EQUALS -> NOT_EQUALS;
            case NOT_EQUALS -> EQUALS;
            default -> null;
        };
    }
}
