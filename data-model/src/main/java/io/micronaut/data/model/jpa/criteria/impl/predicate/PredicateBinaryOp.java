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
    RLIKE,
    ILIKE,
    LIKE,
    REGEX,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    STARTS_WITH_IGNORE_CASE,
    ENDS_WITH_IGNORE_CASE,
    ;

    @Nullable
    public PredicateBinaryOp negate() {
        switch (this) {
            case EQUALS:
                return NOT_EQUALS;
            case NOT_EQUALS:
                return EQUALS;
            default:
                return null;
        }
    }
}
