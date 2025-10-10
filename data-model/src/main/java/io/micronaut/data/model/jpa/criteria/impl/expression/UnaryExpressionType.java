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
package io.micronaut.data.model.jpa.criteria.impl.expression;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import jakarta.persistence.criteria.Expression;

/**
 * The aggregate type.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public enum UnaryExpressionType {
    AVG, SUM, MAX, MIN, COUNT, COUNT_DISTINCT, UPPER, LOWER, LENGTH;

    void validate(Expression<?> expression) {
        switch (this) {
            case AVG, SUM -> CriteriaUtils.requireNumericExpression(expression);
            case MAX, MIN -> CriteriaUtils.requireComparableExpression(expression);
            case UPPER, LOWER, LENGTH -> CriteriaUtils.requireStringExpression(expression);
            case COUNT, COUNT_DISTINCT -> CriteriaUtils.requirePropertyOrRoot(expression);
            default -> throw new IllegalStateException("Unexpected value: " + this);
        }
    }
}
